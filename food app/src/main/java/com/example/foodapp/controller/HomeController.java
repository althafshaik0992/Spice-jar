package com.example.foodapp.controller;

import com.example.foodapp.model.ChatProductDTO;
import com.example.foodapp.model.Product;
import com.example.foodapp.service.CategoryService;
import com.example.foodapp.service.ProductService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;


import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;

import static org.springframework.data.jpa.domain.AbstractPersistable_.id;

@Controller

public class HomeController {


    private final ProductService productService;



   private ChatProductDTO chatProduct;

    private final CategoryService categoryService;



    public HomeController(ProductService productService, CategoryService categoryService) {
        this.productService = productService;

        this.categoryService = categoryService;
    }










    @GetMapping("/")
    public String index(Model m) {
        m.addAttribute("products", productService.findAll());
        return "index";
    }





    @GetMapping("/about")
    public String about(Model m, HttpSession session) {
//        var user = session.getAttribute("USER");
//        if (user == null) return "redirect:/login";
        m.addAttribute("products", productService.findAll());
        return "about";
    }

    @GetMapping("/category/{id}")
    public String byCategory(@PathVariable Long id, Model m){
        m.addAttribute("products", productService.findByCategoryId(id));
        return "index";
    }


    @PostMapping(
            value = "/api/chat",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseBody
    public <chatProduct> Map<String, Object> chat(@RequestBody Map<String, String> body, HttpSession session) {
        String msg = (body.getOrDefault("message", "") + "").trim();
        Map<String, Object> out = new HashMap<>();

        // 1) “add … to cart” intent using previous suggestions
        String lower = msg.toLowerCase(Locale.ROOT);
        if (lower.startsWith("add") || lower.startsWith("i want")) {
            @SuppressWarnings("unchecked")
            List<ChatProductDTO> last = (List<ChatProductDTO>) session.getAttribute("CHAT_SUGG");
            if (last != null && !last.isEmpty()) {
                // very simple: add the first suggested product
                ChatProductDTO pick = last.get(0);
                out.put("reply", "Added “" + pick.getName() + "” to your cart. 🛒");
                // Option A: Let the front-end call your existing /add-to-cart (POST) itself.
                // Return back the product id so the widget can POST with CSRF.
                out.put("addToCartProductId", pick.getId());
                return out;
            } else {
                out.put("reply", "I don’t have a product selected yet. Ask me for a spice and I’ll suggest a few!");
                return out;
            }
        }

        // 2) product / category search
        List<ChatProductDTO> suggestions = tryFindProducts(msg);
        if (!suggestions.isEmpty()) {
            session.setAttribute("CHAT_SUGG", suggestions);
            out.put("reply", "Here are a few matches. Want me to add one?");
            out.put("cards", suggestions); // front-end will render cards with Add buttons
            return out;
        }

        // 3) fallback to your existing rules
        out.put("reply", answer(msg));
        return out;
    }

    private List<ChatProductDTO> tryFindProducts(String q) {
        String s = q.trim();
        if (s.isEmpty()) return List.of();

        // Heuristic: look by name first; if empty, try category words
        List<Product> byName = productService.searchByName(s);
        if (!byName.isEmpty()) return mapProducts(byName);

        // Split to tokens and try each token as category word
        for (String token : s.split("\\s+")) {
            if (token.length() < 3) continue;
            List<Product> byCat = productService.searchByCategoryName(token);
            if (!byCat.isEmpty()) return mapProducts(byCat);
        }
        return List.of();
    }

    private List<ChatProductDTO> mapProducts(List<Product> products) {
        List<ChatProductDTO> list = new ArrayList<>();
        for (Product p : products) {
            String price = (p.getPrice() == null) ? "0.00" : p.getPrice().toPlainString();
            String img = (p.getImageUrl() == null || p.getImageUrl().isBlank()) ? "/images/chilli%20powder.jpeg" : p.getImageUrl();
            list.add(new ChatProductDTO(p.getId(), p.getName(), price, img));
        }
        return list;
    }

    // (Optional) simple GET probe for quick testing in the browser
    @GetMapping(value = "/api/chat", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String,String>> probe() {
        return ResponseEntity.ok(Map.of("reply", "POST {\"message\":\"...\"} here."));
    }



    private String answer(String msg) {
        String m = msg.toLowerCase(Locale.ROOT);

        if (m.contains("best seller") || m.contains("popular")) {
            return "Our best sellers this week are Turmeric Powder, Garam Masala, and Organic Cumin Seeds.";
        }
        if (m.contains("delivery") || m.contains("shipping")) {
            return "Standard delivery: 2–4 days. Express: 1–2 days. Orders over $49 ship free.";
        }
        if (m.contains("return") || m.contains("refund")) {
            return "Returns accepted within 30 days for unopened items.";
        }
        return "I can help with products, delivery, and orders. Try asking: 'Do you have organic turmeric?'";
    }


    @GetMapping("/menu")
    public String menu(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal min,
            @RequestParam(required = false) BigDecimal max,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false, defaultValue = "nameAsc") String sort,
            Model m,HttpSession session
    ) {
//        var user = session.getAttribute("USER");
//        if (user == null) return "redirect:/login";
        m.addAttribute("products", productService.filter(q, min, max, categoryId, sort));
        m.addAttribute("categories", categoryService.findAll());

        // keep the current filter values so the form shows them
        m.addAttribute("q", q);
        m.addAttribute("min", min);
        m.addAttribute("max", max);
        m.addAttribute("categoryId", categoryId);
        m.addAttribute("sort", sort);

        return "menu"; // your menu.html
    }


}






