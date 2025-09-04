package com.example.foodapp.service;

import com.example.foodapp.model.Product;
import com.example.foodapp.repository.ProductRepository;
import com.example.foodapp.repository.ProductSpecs;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class ProductService {
    private final ProductRepository repo;

    public ProductService(ProductRepository repo) {
        this.repo = repo;
    }

    public List<Product> findAll() {
        return repo.findAllWithCategory();
    } // eager load category for view

    public Product save(Product p) {
        return repo.save(p);
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }

    public Product findById(Long id) {
        return repo.findById(id).orElse(null);
    }

    public List<Product> findByCategoryId(Long categoryId) {
        return repo.findByCategory_Id(categoryId);
    }

    public List<Product> searchByName(String q) {
        return repo.findTop5ByNameContainingIgnoreCase(q);
    }

    public List<Product> searchByCategoryName(String q) {
        return repo.findTop5ByCategory_NameContainingIgnoreCase(q);
    }

    public List<Product> filter(String q, BigDecimal min, BigDecimal max, Long categoryId, String sort) {
        List<Product> list = repo.filter(
                (q == null || q.isBlank()) ? null : q.trim(),
                min,
                max,
                categoryId
        );

        if (sort == null) return list;

        switch (sort) {
            case "nameAsc"  -> list.sort(Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER));
            case "nameDesc" -> list.sort(Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER).reversed());
            case "priceAsc" -> list.sort(Comparator.comparing(p -> p.getPrice() == null ? BigDecimal.ZERO : p.getPrice()));
            case "priceDesc"-> list.sort(Comparator.comparing((Product p) -> p.getPrice() == null ? BigDecimal.ZERO : p.getPrice()).reversed());
            default -> { /* leave as-is */ }
        }
        return list;
    }
}
