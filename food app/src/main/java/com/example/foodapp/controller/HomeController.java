package com.example.foodapp.controller;

import com.example.foodapp.model.ChatProductDTO;
import com.example.foodapp.model.Coupon;
import com.example.foodapp.model.Product;
import com.example.foodapp.model.ProductVariant;
import com.example.foodapp.repository.CouponRepository;
import com.example.foodapp.service.CategoryService;
import com.example.foodapp.service.CouponService;
import com.example.foodapp.service.ProductService;
import com.example.foodapp.service.ReviewService;
import com.example.foodapp.util.Cart;
import com.example.foodapp.util.CartUtils;
import com.example.foodapp.util.GlobalData;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.data.jpa.domain.AbstractPersistable_.id;

@Controller

public class HomeController {


    private final ProductService productService;

    private final ReviewService reviewService;



    private ChatProductDTO chatProduct;

    private final CategoryService categoryService;



    private final CouponRepository couponRepository;



    public HomeController(ProductService productService, ReviewService reviewService, CategoryService categoryService,  CouponRepository couponRepository) {
        this.productService = productService;
        this.reviewService = reviewService;
        this.categoryService = categoryService;
        this.couponRepository = couponRepository;
    }









    @GetMapping("/")
    public String index(Model m, HttpSession session) {
        var today = java.time.LocalDate.now();

        java.util.List<com.example.foodapp.model.Coupon> coupons;
        try {
            coupons = couponRepository.findAll(); // or your typed finder
        } catch (Exception e) {
            coupons = java.util.Collections.emptyList();
        }

        coupons = coupons.stream()
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .filter(c -> c.getStartsOn() == null || !today.isBefore(c.getStartsOn()))
                .filter(c -> c.getExpiresOn() == null || !today.isAfter(c.getExpiresOn()))
                .sorted(java.util.Comparator
                        .comparing((com.example.foodapp.model.Coupon c) ->
                                c.getStartsOn() == null ? java.time.LocalDate.MIN : c.getStartsOn())
                        .thenComparing(com.example.foodapp.model.Coupon::getCode,
                                String.CASE_INSENSITIVE_ORDER))
                .toList();

        System.out.println(">>> availableCoupons size = " + coupons.size()); // DEBUG

        m.addAttribute("availableCoupons", coupons);
        m.addAttribute("couponCount", coupons.size()); // DEBUG helper

        m.addAttribute("products", productService.findAll());
        var cart = session.getAttribute("CART");
        m.addAttribute("cartCount", cart != null
                ? com.example.foodapp.util.CartUtils.getCartTotalQuantity((com.example.foodapp.util.Cart) cart)
                : 0);
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


//    @PostMapping(
//            value = "/api/chat",
//            consumes = MediaType.APPLICATION_JSON_VALUE,
//            produces = MediaType.APPLICATION_JSON_VALUE
//    )
//    @ResponseBody
//    public Map<String, Object> chat(@RequestBody(required = false) Map<String, String> body,
//                                    HttpSession session) {
//        String msg = (body == null ? "" : String.valueOf(body.getOrDefault("message", ""))).trim();
//        Map<String, Object> out = new HashMap<>();
//
//        if (msg.isEmpty()) {
//            out.put("reply", "Please type a message, e.g. “Show best sellers”.");
//            return out;
//        }
//
//        // 1) “add … to cart”
//        String lower = msg.toLowerCase(Locale.ROOT);
//        if (lower.startsWith("add") || lower.startsWith("i want")) {
//            @SuppressWarnings("unchecked")
//            List<ChatProductDTO> last = (List<ChatProductDTO>) session.getAttribute("CHAT_SUGG");
//            if (last != null && !last.isEmpty()) {
//                ChatProductDTO pick = last.get(0);
//                out.put("reply", "Added “" + pick.getName() + "” to your cart. 🛒");
//                // Let the front-end POST to /cart/add with CSRF:
//                out.put("addToCartProductId", pick.getId());
//                return out;
//            } else {
//                out.put("reply", "I don’t have a product selected yet. Ask me for a spice and I’ll suggest a few!");
//                return out;
//            }
//        }
//
//        // 2) product / category search
//        List<ChatProductDTO> suggestions = tryFindProducts(msg);
//        if (!suggestions.isEmpty()) {
//            session.setAttribute("CHAT_SUGG", suggestions);
//            out.put("reply", "Here are a few matches. Want me to add one?");
//            out.put("cards", suggestions);
//            return out;
//        }
//
//        // 3) fallback
//        out.put("reply", answer(msg));
//        return out;
//    }

    private List<ChatProductDTO> tryFindProducts(String q) {
        String s = q.trim();
        if (s.isEmpty()) return List.of();

        List<Product> byName = productService.searchByName(s);
        if (!byName.isEmpty()) return mapProducts(byName);

        for (String token : s.split("\\s+")) {
            if (token.length() < 3) continue;
            List<Product> byCat = productService.searchByCategoryName(token);
            if (!byCat.isEmpty()) return mapProducts(byCat);
        }
        return List.of();
    }

    private List<ChatProductDTO> mapProducts(List<Product> products) {
        return products.stream().map(p -> {
            String price = (p.getPrice() == null) ? "0.00" : p.getPrice().setScale(2, RoundingMode.HALF_UP).toPlainString();
            String img = (p.getImageUrl() == null || p.getImageUrl().isBlank())
                    ? "/images/chilli%20powder.jpeg"
                    : p.getImageUrl();
            return new ChatProductDTO(p.getId(), p.getName(), price, img);
        }).collect(Collectors.toList());
    }

    // Simple canned answers to keep things deterministic
    private String answer(String msg) {
        String m = msg.toLowerCase(Locale.ROOT);
        if (m.contains("best seller") || m.contains("popular"))
            return "Our best sellers this week are Turmeric Powder, Garam Masala, and Organic Cumin Seeds.";
        if (m.contains("delivery") || m.contains("shipping"))
            return "Standard delivery: 2–4 days. Express: 1–2 days. Orders over $49 ship free.";
        if (m.contains("return") || m.contains("refund"))
            return "Returns accepted within 30 days for unopened items.";
        return "I can help with products, delivery, and orders. Try asking: “Do you have organic turmeric?”";
    }





    @GetMapping("/menu")
    public String menu(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal min,
            @RequestParam(required = false) BigDecimal max,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false, defaultValue = "nameAsc") String sort,
            Model m, HttpSession session
    ) {
        Cart cart = (Cart) session.getAttribute("CART");
        int cartCount = (cart != null) ? cart.getTotalQuantity() : 0;

        // 1) get the products ONCE
        List<Product> products = productService.filter(q, min, max, categoryId, sort);
        m.addAttribute("products", products);

        // 2) compute maps using the SAME list
        Map<Long, Double> avgMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> reviewService.avg(p)));
        Map<Long, Long> cntMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> reviewService.count(p)));

        // 3) add maps
        m.addAttribute("avgMap", avgMap);
        m.addAttribute("cntMap", cntMap);

        // other model data
        m.addAttribute("categories", categoryService.findAll());
        m.addAttribute("q", q);
        m.addAttribute("min", min);
        m.addAttribute("max", max);
        m.addAttribute("categoryId", categoryId);
        m.addAttribute("sort", sort);
        m.addAttribute("cartCount", cartCount);

        return "menu";
    }


//
//@GetMapping("/menu")
//public String menu(
//        @RequestParam(value = "q", required = false) String q,
//        @RequestParam(value = "min", required = false) Double min,
//        @RequestParam(value = "max", required = false) Double max,
//        Model model
//) {
//    List<Product> products = productService.findAll();
//
//    // ✅ Normalize: always set price/weight for products with variants
//    for (Product p : products) {
//        if (p.getVariants() != null && !p.getVariants().isEmpty()) {
//            p.getVariants().sort(Comparator.comparing(ProductVariant::getPrice));
//            ProductVariant cheapest = p.getVariants().get(0);
//            p.setPrice(cheapest.getPrice());
//            p.setWeight(cheapest.getWeight());
//        }
//    }
//
//    model.addAttribute("products", products);
//    return "menu"; // menu.html view
//}


    private List<Coupon> loadActiveCouponsSafely() {
        try {
            // If you chose Option B in the repo, use this:
            return couponRepository.findActiveCurrentlyValid(LocalDate.now());

            // If you chose Option A instead, comment the line above and uncomment this:
            // return couponRepository.findByActiveTrueOrderByStartsOnAscCodeAsc();

        } catch (Exception ignored) {
            // Fallback: filter + sort in memory
            var today = LocalDate.now();
            return couponRepository.findAll().stream()
                    .filter(c -> Boolean.TRUE.equals(c.getActive()))
                    .filter(c ->
                            (c.getStartsOn() == null || !today.isBefore(c.getStartsOn())) &&
                                    (c.getExpiresOn() == null || !today.isAfter(c.getExpiresOn()))
                    )
                    .sorted(Comparator
                            .comparing((Coupon c) -> c.getStartsOn() == null ? LocalDate.MIN : c.getStartsOn())
                            .thenComparing(Coupon::getCode, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
    }


}
