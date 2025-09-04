package com.example.foodapp.controller;

import com.example.foodapp.service.ProductService;
import com.example.foodapp.util.Cart;
import com.example.foodapp.util.CartItem;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/cart")
public class CartController {
    private final ProductService productService;

    public CartController(ProductService productService) {
        this.productService = productService;
    }

    private Cart getCart(HttpSession session) {
        Cart cart = (Cart) session.getAttribute("CART");
        if (cart == null) {
            cart = new Cart();
            session.setAttribute("CART", cart);
        }
        return cart;
    }


    // CartController.addToCart(...)
    @PostMapping("/add")
    public String addToCart(@RequestParam Long productId,
                            @RequestParam(defaultValue = "1") int qty,
                            HttpSession session) {
        var user = session.getAttribute("USER");
        if (user == null) return "redirect:/login";
        var p = productService.findById(productId);
        if (p != null) {
            String img = (p.getImageUrl() == null || p.getImageUrl().isBlank())
                    ? "/images/chilli%20powder.jpeg"
                    : p.getImageUrl();
            CartItem item = new CartItem(p.getId(), p.getName(), qty, p.getPrice(), img);
            getCart(session).add(item);
        }
        return "redirect:/cart/view";
    }

    @GetMapping("/view")
    public String viewCart(Model m, HttpSession session) {
        var user = session.getAttribute("USER");
        if (user == null) return "redirect:/login";
        Cart cart = getCart(session);
        m.addAttribute("cart", cart);
        return "cart";
    }

    @PostMapping("/update")
    public String update(@RequestParam Long productId, @RequestParam int qty, HttpSession session) {
        var user = session.getAttribute("USER");
        if (user == null) return "redirect:/login";
        getCart(session).update(productId, qty);
        return "redirect:/cart/view";
    }

    @PostMapping("/clear")
    public String clear(HttpSession session) {
        var user = session.getAttribute("USER");
        if (user == null) return "redirect:/login";
        getCart(session).clear();
        return "redirect:/cart/view";
    }
}
