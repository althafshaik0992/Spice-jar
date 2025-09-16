package com.example.foodapp.controller;

import com.example.foodapp.service.ProductService;
import com.example.foodapp.util.Cart;
import com.example.foodapp.util.CartItem;
import com.example.foodapp.util.CartUtils;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/cart")
public class CartController {

    private final ProductService productService;

    public CartController(ProductService productService) {
        this.productService = productService;
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
        m.addAttribute("cart", getOrCreateCart(session));
        // Using the session cart's size, not GlobalData
        m.addAttribute("cartCount", CartUtils.getCartTotalQuantity(getOrCreateCart(session)));
        return "cart";
    }

    @PostMapping("/add")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addToCart(@RequestParam Long productId,
                                                         @RequestParam(defaultValue = "1") int qty,
                                                         HttpSession session) {
        var user = session.getAttribute("USER");
        Map<String, Object> out = new HashMap<>();

//        if (user == null) {
//            out.put("status", "error");
//            out.put("message", "User not logged in.");
//                // Updated to return a redirect to the login page
//                return ResponseEntity.status(302).header("Location", "/login").build();
//            }



        var p = productService.findById(productId);
        if (p != null) {
            Cart cart = getOrCreateCart(session);
            Optional<CartItem> existingItem = cart.getItems().stream()
                    .filter(item -> item.getProductId().equals(productId))
                    .findFirst();

            if (existingItem.isPresent()) {
                existingItem.get().setQty(existingItem.get().getQty() + qty);
            } else {
                String img = (p.getImageUrl() == null || p.getImageUrl().isBlank())
                        ? "/images/chilli%20powder.jpeg"
                        : p.getImageUrl();
                CartItem newItem = new CartItem(p.getId(), p.getName(), qty, p.getPrice(), img);
                cart.getItems().add(newItem);
            }

            out.put("status", "success");
            out.put("cartCount", CartUtils.getCartTotalQuantity(cart));
            out.put("message", "Item added to cart successfully.");
        } else {
            out.put("status", "error");
            out.put("message", "Product not found.");
        }

        return ResponseEntity.ok(out);
    }

    /** Update quantity OR delete (qty=0) for an item currently in the cart */
    @PostMapping("/update")
    public String update(
            @RequestParam("productId") Long productId,
            @RequestParam("qty") int qty,
            HttpSession session
    ) {
        Cart cart = getOrCreateCart(session);

        // find in "items"
        var items = cart.getItems();
        for (Iterator<CartItem> it = items.iterator(); it.hasNext();) {
            CartItem ci = it.next();
            if (ci.getProductId().equals(productId)) {
                if (qty <= 0) {
                    // delete
                    it.remove();
                } else {
                    ci.setQty(qty);
                }
                break;
            }
        }
        session.setAttribute("CART", cart);
        return "redirect:/cart/view";
    }

    /** Move one product from items -> savedForLater */
    @PostMapping("/saveForLater")
    public String saveForLater(
            @RequestParam("productId") Long productId,
            HttpSession session
    ) {
        Cart cart = getOrCreateCart(session);

        Optional<CartItem> found = cart.getItems().stream()
                .filter(ci -> ci.getProductId().equals(productId))
                .findFirst();

        found.ifPresent(ci -> {
            // remove from items
            cart.getItems().remove(ci);

            // if it's already in SFL, bump qty; else add
            Optional<CartItem> inSaved = cart.getSavedForLater().stream()
                    .filter(s -> s.getProductId().equals(productId))
                    .findFirst();

            if (inSaved.isPresent()) {
                CartItem s = inSaved.get();
                s.setQty(s.getQty() + ci.getQty());
            } else {
                cart.getSavedForLater().add(new CartItem(
                        ci.getProductId(),
                        ci.getName(),
                        ci.getQty(),
                        ci.getPrice() == null ? BigDecimal.ZERO : ci.getPrice(),
                        ci.getImageUrl()
                ));
            }
        });

        session.setAttribute("CART", cart);
        // land on saved tab after action
        return "redirect:/cart/view#saved-tab";
    }

    /** Move product from savedForLater -> items (merge qty if already there) */
    @PostMapping("/moveToCart")
    public String moveToCart(@RequestParam Long productId, HttpSession session) {
        Cart cart = getOrCreateCart(session);

        // find the item in savedForLater
        if (cart.getSavedForLater() != null) {
            var it = cart.getSavedForLater().stream()
                    .filter(i -> i.getProductId().equals(productId))
                    .findFirst()
                    .orElse(null);

            if (it != null) {
                // remove from saved
                cart.getSavedForLater().remove(it);
                // add to active cart
                cart.addItem(it);
            }
        }

        return "redirect:/cart/view";
    }

    /** Optional: clear entire cart */
    @PostMapping("/clear")
    public String clear(HttpSession session) {
        session.setAttribute("CART", new Cart());
        return "redirect:/cart/view";
    }
}