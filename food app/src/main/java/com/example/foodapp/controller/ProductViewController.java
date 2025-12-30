// src/main/java/com/example/foodapp/controller/ProductViewController.java
package com.example.foodapp.controller;

import com.example.foodapp.model.Product;
import com.example.foodapp.model.Review;
import com.example.foodapp.model.User;
import com.example.foodapp.util.Cart;
import com.example.foodapp.service.ProductService;
import com.example.foodapp.service.ReviewService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Controller
public class ProductViewController extends BaseController {

    private final ProductService productService;
    private final ReviewService reviewService;

    public ProductViewController(ProductService productService,
                                 ReviewService reviewService) {
        this.productService = productService;
        this.reviewService = reviewService;
    }

    // GET /product/{id} – show product details + reviews
    @GetMapping("/product/{id}")
    public String viewProduct(@PathVariable Long id, Model m, HttpSession session) {
        Product p = productService.findById(id);
        if (p == null) return "redirect:/menu";

        Cart cart = (Cart) session.getAttribute("CART");
        int cartCount = (cart != null) ? cart.getTotalQuantity() : 0;

        double avg = reviewService.avg(p);
        long count = reviewService.count(p);

        m.addAttribute("product", p);
        m.addAttribute("avg", avg);
        m.addAttribute("count", count);
        m.addAttribute("reviews", reviewService.findByProduct(p));
        m.addAttribute("cartCount", cartCount);
        return "product-view";
    }


    // POST /reviews/add – save a review then redirect back

    @PostMapping("/reviews/add")
    public String addReview(
            @RequestParam Long productId,
            @RequestParam int rating,
            @RequestParam String comment,
            HttpSession session
    ) {
        // 1) Find product
        Product p = productService.findById(productId);
        if (p == null) {
            return "redirect:/menu";
        }

        // 2) Must be logged in to add review (because Review.user is @ManyToOne(optional=false))
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        // 3) Build Review
        Review r = new Review();
        r.setProduct(p);
        r.setUser(user);                 // ✅ set the User entity, not name/string
        r.setRating(rating);
        r.setTitle("");// if you want, or add title param in form
        r.setComment(comment);
        r.setCreatedAt(Instant.now());   // ✅ matches Instant type in entity
        r.setApproved(true);             // or false if you want moderation

        reviewService.save(r);

        // 4) Back to product detail page
        return "redirect:/product/" + productId;
    }
}
