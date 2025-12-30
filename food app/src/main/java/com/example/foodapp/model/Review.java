// src/main/java/com/example/foodapp/model/Review.java
package com.example.foodapp.model;

import jakarta.persistence.*;
import lombok.Getter; import lombok.Setter;
import java.time.Instant;

@Entity @Getter @Setter
@Table(name = "reviews", indexes = {
        @Index(name="idx_reviews_product", columnList = "product_id"),
        @Index(name="idx_reviews_createdAt", columnList = "createdAt")
})
public class Review {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;





    @ManyToOne(optional=false, fetch=FetchType.LAZY)
    private Product product;

    @ManyToOne(optional=false, fetch=FetchType.LAZY)
    private User user;

    @Column(nullable=false) private int rating;          // 1..5
    @Column(nullable=false) private String title;
    @Column(nullable=false, length = 4000) private String comment;

    @Column(nullable=false) private Instant createdAt = Instant.now();

    // Simple moderation flag if needed
    @Column(nullable=false) private boolean approved = true;
}
