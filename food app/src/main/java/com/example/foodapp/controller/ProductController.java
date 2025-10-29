// src/main/java/com/example/foodapp/controller/ProductController.java
package com.example.foodapp.controller;

import com.example.foodapp.model.Product;
import com.example.foodapp.model.User;
import com.example.foodapp.service.ProductService;
import com.example.foodapp.service.ReviewService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/products")
public class ProductController extends BaseController {

    private final ProductService productService;
    private final ReviewService reviewService;

    public ProductController(ProductService productService, ReviewService reviewService) {
        this.productService = productService;
        this.reviewService = reviewService;
    }

    @GetMapping("/products/{id}")
    public String details(@PathVariable Long id, Model m, HttpSession session) {
        var p = productService.findById(id);
        if (p == null) throw new IllegalArgumentException("Product not found");
        m.addAttribute("product", p);

        double avg = reviewService.avg(p);        // 0.0–5.0
        long count = reviewService.count(p);      // number of reviews
        m.addAttribute("avgRating", avg);
        m.addAttribute("reviewCount", count);

        var user = currentUser(session);
        boolean reviewed = user != null && reviewService.userAlreadyReviewed(p.getId(), user.getId());
        m.addAttribute("canReview", user != null && !reviewed);

        m.addAttribute("reviews", reviewService.list(p));  // List<Review> with getRating(), getTitle(), getComment(), etc.
        return "product";
    }


    @PostMapping("/{id}/reviews")
    public String addReview(@PathVariable Long id,
                            @RequestParam int rating,
                            @RequestParam String title,
                            @RequestParam String comment,
                            HttpSession session,
                            Model model) {
        Product p = productService.findById(id);
        if (p == null) throw new IllegalArgumentException("Product not found");

        User user = currentUser(session);
        if (user == null) return "redirect:/login";

        if (reviewService.userAlreadyReviewed(id, user.getId())) {
            return "redirect:/products/" + id + "?alreadyReviewed=1";
        }

        // (optional) simple server-side checks
        if (rating < 1 || rating > 5 || title.isBlank() || comment.isBlank()) {
            return "redirect:/products/" + id + "?invalid=1";
        }

        reviewService.add(p, user, rating, title, comment);
        return "redirect:/products/" + id + "?reviewed=1";
    }

}
