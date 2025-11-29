package com.example.foodapp.service;

import com.example.foodapp.model.Product;
import com.example.foodapp.repository.ProductRepository;
import com.example.foodapp.repository.ProductSpecs;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

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

//    public List<Product> filter(String q, BigDecimal min, BigDecimal max, Long categoryId, String sort) {
//        List<Product> list = repo.filter(
//                (q == null || q.isBlank()) ? null : q.trim(),
//                min,
//                max,
//                categoryId
//        );
//
//        if (sort == null) return list;
//
//        switch (sort) {
//            case "nameAsc"  -> list.sort(Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER));
//            case "nameDesc" -> list.sort(Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER).reversed());
//            case "priceAsc" -> list.sort(Comparator.comparing(p -> p.getPrice() == null ? BigDecimal.ZERO : p.getPrice()));
//            case "priceDesc"-> list.sort(Comparator.comparing((Product p) -> p.getPrice() == null ? BigDecimal.ZERO : p.getPrice()).reversed());
//            default -> { /* leave as-is */ }
//        }
//        return list;
//    }

    public List<Product> search(String query, int limit) {
        if (query == null || query.isBlank()) return List.of();

        // Add pagination + fuzzy search
        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(limit, 12)));

        return repo.searchByKeyword(query.toLowerCase(), pageable);
    }



    public long count() {
        return repo.count();
    }

    public List<Product> findLowStock(int threshold){
        return repo.findLowStock(threshold);
    }


    public List<Product> findAllForMenu() {
        return repo.findAllWithVariantsAndCategory();
    }



    public List<Product> filter(String q,
                                BigDecimal min,
                                BigDecimal max,
                                Long categoryId,
                                String sort) {

        // Raw list can contain duplicates if query joins variants
        List<Product> raw = repo.searchWithVariants(q, min, max, categoryId);

        // --- DEDUPE: keep only the first Product for each id ---
        Map<Long, Product> unique = new LinkedHashMap<>();
        for (Product p : raw) {
            if (p != null && p.getId() != null) {
                unique.putIfAbsent(p.getId(), p);
            }
        }
        List<Product> products = new ArrayList<>(unique.values());

        // --- Sorting (use ONE style of switch; here classic colon style) ---
        if (sort == null) sort = "nameAsc";

        switch (sort) {
            case "nameDesc":
                products.sort(Comparator.comparing(
                        Product::getName,
                        String.CASE_INSENSITIVE_ORDER
                ).reversed());
                break;

            case "priceAsc":
                products.sort(Comparator.comparing(Product::getPrice));
                break;

            case "priceDesc":
                products.sort(Comparator.comparing(Product::getPrice).reversed());
                break;

            case "nameAsc":
            default:
                products.sort(Comparator.comparing(
                        Product::getName,
                        String.CASE_INSENSITIVE_ORDER
                ));
                break;
        }

        return products;
    }


}
