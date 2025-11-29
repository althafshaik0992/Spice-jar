package com.example.foodapp.service;

import com.example.foodapp.model.Order;
import com.example.foodapp.model.OrderItem;
import com.example.foodapp.model.Product;
import com.example.foodapp.model.ProductVariant;
import com.example.foodapp.repository.ProductRepository;
import com.example.foodapp.repository.ProductVariantRepository;
import com.example.foodapp.util.CartItem;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    public static final int DEFAULT_LOW_STOCK_THRESHOLD = 5;

    private final ProductRepository productRepo;
    private final ProductVariantRepository variantRepo;

    public InventoryService(ProductRepository productRepo, ProductVariantRepository variantRepo) {
        this.productRepo = productRepo;
        this.variantRepo = variantRepo;
    }

    /* =========================
       Stock lookups
       ========================= */

    /** Returns current stock for a given productId/variantId combo. Variant wins if present. */
    public int getCurrentStock(Long productId, Long variantId) {
        if (variantId != null) {
            Optional<ProductVariant> v = variantRepo.findById(variantId);
            return v.map(x -> safe(x.getStock())).orElse(0);
        }
        if (productId != null) {
            Optional<Product> p = productRepo.findById(productId);
            return p.map(x -> safe(x.getStock())).orElse(0);
        }
        return 0;
    }

    /** Batch: productId -> stock (base product only). */
    public Map<Long, Integer> getStocksForProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) return Collections.emptyMap();
        List<Product> products = productRepo.findAllById(productIds);
        return products.stream().collect(Collectors.toMap(Product::getId, p -> safe(p.getStock())));
    }

    /** Batch for cart: for each item use variant if present else product. Keyed by productId. */
    // Overload for CartItem
    // For CartItem list – product-level stock only
    public Map<Long, Integer> getStocksForCartItems(Collection<CartItem> items) {
        if (items == null || items.isEmpty()) return java.util.Collections.emptyMap();

        // collect product IDs from the cart
        Set<Long> productIds = items.stream()
                .map(CartItem::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // fetch all products
        Map<Long, Product> productMap = productRepo.findAllById(productIds)
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        // build productId -> stock map
        Map<Long, Integer> out = new HashMap<>();
        for (CartItem it : items) {
            Long pid = it.getProductId();
            if (pid != null) {
                Product p = productMap.get(pid);
                int stock = (p == null || p.getStock() == null) ? 0 : p.getStock();
                out.put(pid, stock);
            }
        }
        return out;
    }



    /* =========================
       Labels & CSS (Amazon-style)
       ========================= */

    public enum StockStatus { OUT, LOW, IN }

    /** Compute status from a numeric stock count and threshold. */
    public StockStatus statusOf(int stock, int lowThreshold) {
        if (stock <= 0) return StockStatus.OUT;
        if (stock <= Math.max(1, lowThreshold)) return StockStatus.LOW;
        return StockStatus.IN;
    }

    /** CSS class matching your templates. */
    public String cssOf(int stock, int lowThreshold) {
        return switch (statusOf(stock, lowThreshold)) {
            case OUT -> "stock-out";
            case LOW -> "stock-low";
            case IN  -> "stock-ok";
        };
    }

    /** Human label (In Stock / Only X left — order soon / Out of stock). */
    public String labelOf(int stock, int lowThreshold) {
        return switch (statusOf(stock, lowThreshold)) {
            case OUT -> "Out of stock";
            case LOW -> "Only " + stock + " left — order soon";
            case IN  -> "In Stock";
        };
    }

    /* =========================
       Order application (decrement)
       ========================= */

    /** Decrement stock for each item in the order (variant preferred). */
    @Transactional
    public void applyOrder(Order order) {
        if (order == null || order.getItems() == null) return;

        for (OrderItem it : order.getItems()) {
            int qty = Math.max(0, it.getQuantity() == null ? 0 : it.getQuantity());

            if (it.getVariantId() != null) {
                variantRepo.findById(it.getVariantId()).ifPresent(v -> {
                    int newQty = Math.max(0, safe(v.getStock()) - qty);
                    v.setStock(newQty);
                    variantRepo.save(v);
                });
            } else if (it.getProductId() != null) {
                productRepo.findById(it.getProductId()).ifPresent(p -> {
                    int newQty = Math.max(0, safe(p.getStock()) - qty);
                    p.setStock(newQty);
                    productRepo.save(p);
                });
            }
        }
    }

    /* =========================
       Helpers
       ========================= */

    private int safe(Integer n) { return n == null ? 0 : n; }
}
