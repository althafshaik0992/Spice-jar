package com.example.foodapp.controller;

import com.example.foodapp.model.Product;
import com.example.foodapp.model.ProductVariant;
import com.example.foodapp.model.User;
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

    public CartController(ProductService productService, UserService userService) {
        this.productService = productService;
        this.userService = userService;
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

    /**
     * Add to cart (supports optional variantId).
     * Returns JSON with name/size so UI can toast "Turmeric — 200 g added".
     */
    @PostMapping("/add")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addToCart(@RequestParam Long productId,
                                                         @RequestParam(required = false) Long variantId,
                                                         @RequestParam(defaultValue = "1") int qty,
                                                         HttpSession session) {
        Map<String, Object> out = new HashMap<>();

        Product p = productService.findById(productId);
        if (p == null) {
            out.put("status", "error");
            out.put("message", "Product not found.");
            return ResponseEntity.ok(out);
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
                return ResponseEntity.ok(out);
            }
            Integer w = chosenVariant.getWeight();
            sizeLabel = (w != null ? w + " g" : "Variant");
            unitPrice = chosenVariant.getPrice();
        } else {
            Integer w = p.getWeight();
            sizeLabel = (w != null ? w + " g" : "Default");
            unitPrice = p.getPrice();
        }

        // We keep display name size-aware so different weights show as separate lines
        String displayName = p.getName() + " — " + sizeLabel;

        Cart cart = getOrCreateCart(session);

        // Merge rule: same product + same display name (therefore same size)
        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> Objects.equals(item.getProductId(), productId)
                        && Objects.equals(item.getName(), displayName))
                .findFirst();

        if (existingItem.isPresent()) {
            existingItem.get().setQty(existingItem.get().getQty() + Math.max(1, qty));
        } else {
            Integer weightForLine = (chosenVariant != null) ? chosenVariant.getWeight() : p.getWeight();
            CartItem newItem = new CartItem(
                    p.getId(),
                    displayName,                           // e.g., "Turmeric — 200 g"
                    Math.max(1, qty),
                    unitPrice,
                    img,
                    p.getDescription(),                     // ✅ description in cart
                    weightForLine                           // ✅ grams in cart
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
                "qty", Math.max(1, qty)
        ));
        out.put("grandTotal", cart.getItems().stream()
                .map(ci -> ci.getPrice().multiply(BigDecimal.valueOf(ci.getQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        return ResponseEntity.ok(out);
    }

    /** Update quantity OR delete (qty=0) by productId (legacy support) */
    @PostMapping("/update")
    public String update(@RequestParam("productId") Long productId,
                         @RequestParam("qty") int qty,
                         HttpSession session) {
        Cart cart = getOrCreateCart(session);
        for (Iterator<CartItem> it = cart.getItems().iterator(); it.hasNext();) {
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

        Optional<CartItem> found = cart.getItems().stream()
                .filter(ci -> Objects.equals(ci.getProductId(), productId))
                .findFirst();

        found.ifPresent(ci -> {
            cart.getItems().remove(ci);

            Optional<CartItem> inSaved = cart.getSavedForLater().stream()
                    .filter(s -> Objects.equals(s.getProductId(), productId)
                            && Objects.equals(s.getName(), ci.getName()))
                    .findFirst();

            if (inSaved.isPresent()) {
                inSaved.get().setQty(inSaved.get().getQty() + ci.getQty());
            } else {
                // ✅ preserve description/weight when saving
                cart.getSavedForLater().add(new CartItem(
                        ci.getProductId(),
                        ci.getName(),
                        ci.getQty(),
                        ci.getPrice() == null ? BigDecimal.ZERO : ci.getPrice(),
                        ci.getImageUrl(),
                        ci.getDescription(),
                        ci.getWeightGrams()
                ));
            }
        });

        session.setAttribute("CART", cart);
        return "redirect:/cart/view#saved-tab";
    }

    /** Move product from savedForLater -> items (merge by productId + display name) */
    @PostMapping("/moveToCart")
    public String moveToCart(@RequestParam Long productId, HttpSession session) {
        Cart cart = getOrCreateCart(session);

        if (cart.getSavedForLater() != null) {
            CartItem it = cart.getSavedForLater().stream()
                    .filter(i -> Objects.equals(i.getProductId(), productId))
                    .findFirst()
                    .orElse(null);

            if (it != null) {
                cart.getSavedForLater().remove(it);

                Optional<CartItem> existing = cart.getItems().stream()
                        .filter(ci -> Objects.equals(ci.getProductId(), it.getProductId())
                                && Objects.equals(ci.getName(), it.getName()))
                        .findFirst();

                if (existing.isPresent()) {
                    existing.get().setQty(existing.get().getQty() + it.getQty());
                } else {
                    cart.getItems().add(it); // same instance carries desc/weight
                }
            }
        }
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
