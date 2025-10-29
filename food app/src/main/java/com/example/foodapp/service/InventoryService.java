package com.example.foodapp.service;

import com.example.foodapp.model.Order;
import com.example.foodapp.model.OrderItem;
import com.example.foodapp.model.Product;
import com.example.foodapp.model.ProductVariant;
import com.example.foodapp.repository.ProductRepository;
import com.example.foodapp.repository.ProductVariantRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {

    private final ProductRepository productRepo;
    private final ProductVariantRepository variantRepo;

    public InventoryService(ProductRepository productRepo, ProductVariantRepository variantRepo) {
        this.productRepo = productRepo;
        this.variantRepo = variantRepo;
    }

    /** Decrement stock for each item in the order. */
    @Transactional
    public void applyOrder(Order order) {
        if (order == null || order.getItems() == null) return;

        for (OrderItem it : order.getItems()) {
            // Prefer variant-level decrement if present
            if (it.getVariantId() != null) {
                ProductVariant v = variantRepo.findById(it.getVariantId()).orElse(null);
                if (v != null) {
                    int newQty = Math.max(0, (v.getStock() == null ? 0 : v.getStock()) - it.getQuantity());
                    v.setStock(newQty);
                    variantRepo.save(v);
                }
            } else {
                // Fall back to base product stock if no variant is used
                Product p = productRepo.findById(it.getProductId()).orElse(null);
                if (p != null) {
                    int stock = p.getStock() == null ? 0 : p.getStock();
                    p.setStock(Math.max(0, stock - it.getQuantity()));
                    productRepo.save(p);
                }
            }
        }
    }
}
