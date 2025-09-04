// ProductSpecs.java
package com.example.foodapp.repository;

import com.example.foodapp.model.Product;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

public final class ProductSpecs {

    private ProductSpecs() {}

    public static Specification<Product> nameContains(String q) {
        if (q == null || q.trim().isEmpty()) return null;
        String like = "%" + q.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), like);
    }

    public static Specification<Product> priceGte(BigDecimal min) {
        if (min == null) return null;
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), min);
    }

    public static Specification<Product> priceLte(BigDecimal max) {
        if (max == null) return null;
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), max);
    }

    public static Specification<Product> inCategory(Long categoryId) {
        if (categoryId == null) return null;
        return (root, query, cb) -> {
            // join only if you use relation
            var category = root.join("category", JoinType.LEFT);
            return cb.equal(category.get("id"), categoryId);
        };
    }
}
