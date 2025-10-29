// src/main/java/com/example/foodapp/service/ReviewService.java
package com.example.foodapp.service;

import com.example.foodapp.model.Product;
import com.example.foodapp.model.Review;
import com.example.foodapp.model.User;
import com.example.foodapp.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReviewService {
    private final ReviewRepository repo;

    public ReviewService(ReviewRepository repo) { this.repo = repo; }

    public List<Review> list(Product p) {
        return repo.findByProductAndApprovedOrderByCreatedAtDesc(p, true);
    }

    public double avg(Product p) { return repo.avgRating(p); }

    public long count(Product p) { return repo.countApproved(p); }

    public boolean userAlreadyReviewed(Long productId, Long userId) {
        return repo.existsByProductIdAndUserId(productId, userId);
    }

    @Transactional
    public Review add(Product p, User u, int rating, String title, String comment) {
        Review r = new Review();
        r.setProduct(p);
        r.setUser(u);
        r.setRating(Math.max(1, Math.min(5, rating)));
        r.setTitle(title == null ? "" : title.trim());
        r.setComment(comment == null ? "" : comment.trim());
        r.setApproved(true); // flip to false if you want moderation
        return repo.save(r);
    }
}
