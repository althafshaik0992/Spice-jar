package com.example.foodapp.controller;

import com.example.foodapp.model.Product;
import com.example.foodapp.model.ProductVariant;
import com.example.foodapp.model.User;
import com.example.foodapp.service.InventoryService;
import com.example.foodapp.service.ProductService;
import com.example.foodapp.service.UserService;
import com.example.foodapp.util.Cart;
import com.example.foodapp.util.CartItem;
import com.example.foodapp.util.CartUtils;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@Controller
@RequestMapping("/cart")
public class CartController {

    private final ProductService productService;
    private final UserService userService;
    private final InventoryService inventoryService;

    public CartController(ProductService productService,
                          UserService userService,
                          InventoryService inventoryService) {
        this.productService = productService;
        this.userService = userService;
        this.inventoryService = inventoryService;
    }

    /** Ensure we always have a Cart object in the session */
    private Cart getOrCreateCart(HttpSession session) {
        Cart cart = (Cart) session.getAttribute("CART");
        if (cart == null) {
            cart = new Cart();
            session.setAttribute("CART", cart);
        }
        return cart;
    }

    /** Show cart page */
    @GetMapping("/view")
    public String view(Model m, HttpSession session) {
        Cart cart = getOrCreateCart(session);
        m.addAttribute("cart", cart);
        m.addAttribute("cartCount", CartUtils.getCartTotalQuantity(cart));
        return "cart";
    }

    // ------------------------------------------------------------
    //  INTERNAL helper used by BOTH add endpoints
    // ------------------------------------------------------------
    private Map<String, Object> doAddToCart(Long productId,
                                            Long variantId,
                                            int qty,
                                            HttpSession session) {

        Map<String, Object> out = new HashMap<>();

        Product p = productService.findById(productId);
        if (p == null) {
            out.put("status", "error");
            out.put("message", "Product not found.");
            return out;
        }

        // Basic stock check using InventoryService
        Map<Long, Integer> stockMap =
                inventoryService.getStocksForProductIds(Collections.singletonList(productId));
        Integer stock = (stockMap != null) ? stockMap.getOrDefault(productId, 0) : null;

        if (stock != null) {
            if (stock <= 0) {
                out.put("status", "error");
                out.put("message", "This item is currently out of stock.");
                return out;
            }
            if (qty > stock) {
                out.put("status", "error");
                out.put("message", "Only " + stock + " left in stock.");
                return out;
            }
        }

        // Resolve image
        String img = (p.getImageUrl() == null || p.getImageUrl().isBlank())
                ? "/images/chilli%20powder.jpeg"
                : p.getImageUrl();

        // Resolve size + unit price (+ chosen variant if provided)
        String sizeLabel;
        BigDecimal unitPrice;
        ProductVariant chosenVariant = null;

        if (variantId != null) {
            if (p.getVariants() != null) {
                for (ProductVariant v : p.getVariants()) {
                    if (v != null && Objects.equals(v.getId(), variantId)) {
                        chosenVariant = v;
                        break;
                    }
                }
            }
            if (chosenVariant == null) {
                out.put("status", "error");
                out.put("message", "Variant not found for product.");
                return out;
            }
            Integer w = chosenVariant.getWeight();
            sizeLabel = (w != null ? w + " g" : "Variant");
            unitPrice = chosenVariant.getPrice();
        } else {
            Integer w = p.getWeight();
            sizeLabel = (w != null ? w + " g" : "Default");
            unitPrice = p.getPrice();
        }

        // display name size-aware so different weights show as separate lines
        String displayName = p.getName() + " — " + sizeLabel;

        Cart cart = getOrCreateCart(session);

        // Merge rule: same product + same display name (therefore same size)
        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> Objects.equals(item.getProductId(), productId)
                        && Objects.equals(item.getName(), displayName))
                .findFirst();

        int safeQty = Math.max(1, qty);

        if (existingItem.isPresent()) {
            existingItem.get().setQty(existingItem.get().getQty() + safeQty);
        } else {
            Integer weightForLine = (chosenVariant != null) ? chosenVariant.getWeight() : p.getWeight();
            CartItem newItem = new CartItem(
                    p.getId(),
                    displayName,                           // e.g., "Turmeric — 200 g"
                    safeQty,
                    unitPrice,
                    img,
                    p.getDescription(),                     // description in cart
                    weightForLine                           // grams in cart
            );
            cart.getItems().add(newItem);
        }

        // JSON for client toast + badge
        out.put("status", "success");
        out.put("cartCount", CartUtils.getCartTotalQuantity(cart));
        out.put("item", Map.of(
                "name", p.getName(),
                "size", sizeLabel,
                "unitPrice", unitPrice,
                "qty", safeQty
        ));
        out.put("grandTotal", cart.getItems().stream()
                .map(ci -> ci.getPrice().multiply(BigDecimal.valueOf(ci.getQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        return out;
    }

    // ------------------------------------------------------------
    // 1) AJAX version   (used by MENU page JS – returns JSON)
    // ------------------------------------------------------------
    @PostMapping(value = "/add", headers = "X-Requested-With=XMLHttpRequest")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addToCartAjax(@RequestParam Long productId,
                                                             @RequestParam(required = false) Long variantId,
                                                             @RequestParam(defaultValue = "1") int qty,
                                                             HttpSession session) {

        Map<String, Object> out = doAddToCart(productId, variantId, qty, session);
        return ResponseEntity.ok(out);
    }

    // ------------------------------------------------------------
    // 2) Non-AJAX version (used by PRODUCT VIEW page – redirects)
    // ------------------------------------------------------------
    @PostMapping("/add")
    public String addToCartNonAjax(@RequestParam Long productId,
                                   @RequestParam(required = false) Long variantId,
                                   @RequestParam(defaultValue = "1") int qty,
                                   @RequestHeader(value = "Referer", required = false) String referer,
                                   HttpSession session) {

        // we ignore the map for now; out-of-stock is already handled in UI
        doAddToCart(productId, variantId, qty, session);

        // send the user back where they came from, or to product page
        if (referer != null && !referer.isBlank()) {
            return "redirect:" + referer;
        }
        return "redirect:/product/" + productId;
    }

    /** Update quantity OR delete (qty=0) by productId (legacy support) */
    @PostMapping("/update")
    public String update(@RequestParam("productId") Long productId,
                         @RequestParam("qty") int qty,
                         HttpSession session) {
        Cart cart = getOrCreateCart(session);
        for (Iterator<CartItem> it = cart.getItems().iterator(); it.hasNext(); ) {
            CartItem ci = it.next();
            if (Objects.equals(ci.getProductId(), productId)) {
                if (qty <= 0) it.remove();
                else ci.setQty(qty);
                break;
            }
        }
        session.setAttribute("CART", cart);
        return "redirect:/cart/view";
    }

    /** Update quantity by line index (matches iter.index in the view) */
    @PostMapping("/qty")
    public String updateQtyByIndex(@RequestParam int index,
                                   @RequestParam int qty,
                                   HttpSession session) {
        Cart cart = getOrCreateCart(session);
        if (index >= 0 && index < cart.getItems().size()) {
            cart.getItems().get(index).setQty(Math.max(1, qty));
        }
        return "redirect:/cart/view";
    }

    /** Remove a line by index (matches iter.index in the view) */
    @PostMapping("/remove")
    public String removeByIndex(@RequestParam int index, HttpSession session) {
        Cart cart = getOrCreateCart(session);
        if (index >= 0 && index < cart.getItems().size()) {
            cart.getItems().remove(index);
        }
        return "redirect:/cart/view";
    }

    /** Move one product from items -> savedForLater */
    @PostMapping("/saveForLater")
    public String saveForLater(@RequestParam("productId") Long productId,
                               HttpSession session) {
        Cart cart = getOrCreateCart(session);

        // Find item in active items
        Optional<CartItem> found = cart.getItems().stream()
                .filter(ci -> Objects.equals(ci.getProductId(), productId))
                .findFirst();

        found.ifPresent(ci -> {
            // Mark item as saved for later
            ci.setSavedForLater(true);

            // Move between lists
            cart.getItems().remove(ci);

            // Merge with existing saved line if present
            Optional<CartItem> inSaved = cart.getSavedForLater().stream()
                    .filter(s -> Objects.equals(s.getProductId(), productId)
                            && Objects.equals(s.getName(), ci.getName()))
                    .findFirst();

            if (inSaved.isPresent()) {
                inSaved.get().setQty(inSaved.get().getQty() + ci.getQty());
            } else {
                cart.getSavedForLater().add(ci);
            }
        });

        session.setAttribute("CART", cart);
        return "redirect:/cart/view#saved-tab";
    }

    /** Move product from savedForLater -> items (merge by productId + display name) */
    @PostMapping("/moveToCart")
    public String moveToCart(@RequestParam Long productId, HttpSession session) {
        Cart cart = getOrCreateCart(session);

        Optional<CartItem> found = cart.getSavedForLater().stream()
                .filter(ci -> Objects.equals(ci.getProductId(), productId))
                .findFirst();

        found.ifPresent(ci -> {
            // Mark item as active again
            ci.setSavedForLater(false);

            // Remove from saved list
            cart.getSavedForLater().remove(ci);

            // Merge with existing active line if present
            Optional<CartItem> existing = cart.getItems().stream()
                    .filter(i -> Objects.equals(i.getProductId(), ci.getProductId())
                            && Objects.equals(i.getName(), ci.getName()))
                    .findFirst();

            if (existing.isPresent()) {
                existing.get().setQty(existing.get().getQty() + ci.getQty());
            } else {
                cart.getItems().add(ci);
            }
        });

        session.setAttribute("CART", cart);
        return "redirect:/cart/view";
    }

    /** Clear entire cart */
    @PostMapping("/clear")
    public String clear(HttpSession session) {
        session.setAttribute("CART", new Cart());
        return "redirect:/cart/view";
    }

    private User currentUser(HttpSession session) {
        try {
            User u = userService.getCurrentUser(session);
            if (u != null) return u;
        } catch (Throwable ignore) { }
        Object s = session != null ? session.getAttribute("USER") : null;
        return (s instanceof User) ? (User) s : null;
    }
}
