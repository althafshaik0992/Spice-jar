// src/main/java/com/example/foodapp/repository/ReviewRepository.java
package com.example.foodapp.repository;

import com.example.foodapp.model.Product;
import com.example.foodapp.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByProductAndApprovedOrderByCreatedAtDesc(Product product, boolean approved);

    @Query("select coalesce(avg(r.rating),0) from Review r where r.product = :product and r.approved = true")
    double avgRating(Product product);

    @Query("select count(r) from Review r where r.product = :product and r.approved = true")
    long countApproved(Product product);

    boolean existsByProductIdAndUserId(Long productId, Long userId); // “one per user” guard (optional)
}
