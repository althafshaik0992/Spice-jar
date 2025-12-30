// src/main/java/com/example/foodapp/controller/GiftCardController.java
package com.example.foodapp.controller;

import com.example.foodapp.model.User;
import com.example.foodapp.service.AddressService;
import com.example.foodapp.service.GiftCardService;
import com.example.foodapp.service.UserService;
import com.example.foodapp.util.Cart;
import com.example.foodapp.util.CartItem;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
@RequestMapping("/gift-cards")
public class GiftCardController {

    private final GiftCardService service;
    private final AddressService addressService;
    private final com.example.foodapp.service.CartService cartService;
    private final UserService userService;

    public GiftCardController(GiftCardService service,
                              AddressService addressService,
                              com.example.foodapp.service.CartService cartService,
                              UserService userService) {
        this.service = service;
        this.addressService = addressService;
        this.cartService = cartService;
        this.userService = userService;
    }

    /* ---------- helper ---------- */

    protected User currentUser(HttpSession session) {
        // If admin is logged in, treat it as NOT a user session
        if (session != null && session.getAttribute("ADMIN_USER") != null) {
            return null;
        }

        Object u = session != null ? session.getAttribute("USER") : null;
        return (u instanceof User) ? (User) u : null;
    }



    private Cart getOrCreateCart(HttpSession session) {
        Cart cart = (Cart) session.getAttribute("CART");
        if (cart == null) {
            cart = new Cart();
            session.setAttribute("CART", cart);
        }
        return cart;
    }

    /* ---------- pages ---------- */


        // -----------------------------------------
        // VIEW ALL CARDS
        // -----------------------------------------
        @GetMapping
        public String index(Model model, HttpSession session, RedirectAttributes ra) {

            User user = currentUser(session);
            if (user == null) {
                ra.addFlashAttribute("flashOk", false);
                ra.addFlashAttribute("flashMsg", "Please login to view your gift cards.");
                return "redirect:/login?next=/gift-cards";
            }

            Long userId = user.getId();

            // 🔥 FIX: Load ALL cards
            var cards = service.findAllForUser(userId);

            model.addAttribute("cards", cards);
            model.addAttribute("cardsCount", cards.size());
            model.addAttribute("totalBalance", service.totalBalance(userId));

            // cart count
            Cart cart = (Cart) session.getAttribute("CART");
            int cartCount = cart == null ? 0 : cart.getTotalQuantity();
            model.addAttribute("cartCount", cartCount);

            return "gift-cards";
        }

        // -----------------------------------------
        // REDEEM CARD
        // -----------------------------------------
        @PostMapping("/redeem")
        public String redeem(@RequestParam String code,
                             HttpSession session,
                             RedirectAttributes ra) {



            User user = currentUser(session);
            if (user == null) {
                ra.addFlashAttribute("flashOk", false);
                ra.addFlashAttribute("flashMsg", "Please login to redeem gift cards.");
                return "redirect:/login?next=/gift-cards";
            }

            try {
                service.redeemToUser(user.getId(), code);
                ra.addFlashAttribute("flashOk", true);
                ra.addFlashAttribute("flashMsg", "Gift card added successfully!");
            } catch (Exception e) {
                ra.addFlashAttribute("flashOk", false);
                ra.addFlashAttribute("flashMsg", e.getMessage());
            }

            return "redirect:/gift-cards";
        }

        // -----------------------------------------
        // REMOVE CARD
        // -----------------------------------------
        @PostMapping("/remove/{id}")
        public String remove(@PathVariable Long id,
                             HttpSession session,
                             RedirectAttributes ra) {

            User user = currentUser(session);
            if (user == null)
                return "redirect:/login";

            try {
                service.removeFromUser(user.getId(), id);
                ra.addFlashAttribute("flashOk", true);
                ra.addFlashAttribute("flashMsg", "Gift card removed.");
            } catch (Exception e) {
                ra.addFlashAttribute("flashOk", false);
                ra.addFlashAttribute("flashMsg", e.getMessage());
            }

            return "redirect:/gift-cards";
        }




    // GET /gift-cards/buy
    @GetMapping("/buy")
    public String buyPage(@RequestParam(value = "shipTo", required = false) Long addressId,
                          HttpSession session,
                          Model model,
                          RedirectAttributes ra) {

        User user = currentUser(session);
        if (user == null) {
            ra.addFlashAttribute("flashOk", false);
            ra.addFlashAttribute("flashMsg", "Please login to buy a gift card.");
            return "redirect:/login?next=/gift-cards/buy";
        }

        var addresses = addressService.addressesForUser(user.getId());
        model.addAttribute("addresses", addresses);
        model.addAttribute("selectedAddressId", addressId);
        model.addAttribute("suggestedEmail", user.getEmail());

        Cart cart = (Cart) session.getAttribute("CART");
        int cartCount = (cart == null) ? 0 : cart.getTotalQuantity();
        model.addAttribute("cartCount", cartCount);

        return "gift-cards-buy";
    }

    // POST /gift-cards/buy
    @PostMapping("/buy")
    public String buySubmit(@RequestParam(required = false) Integer amountPreset,
                            @RequestParam(required = false) BigDecimal customAmount,
                            @RequestParam String delivery, // "EMAIL" | "PHYSICAL"
                            @RequestParam(required = false) String recipientName,
                            @RequestParam(required = false) String recipientEmail,
                            @RequestParam(required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduleDate,
                            @RequestParam(required = false) Long shipToAddressId,
                            @RequestParam(required = false) String fromName,
                            @RequestParam(required = false) String message,
                            HttpSession session,
                            RedirectAttributes ra) {

        User user = currentUser(session);
        if (user == null) {
            ra.addFlashAttribute("flashOk", false);
            ra.addFlashAttribute("flashMsg", "Please login to buy a gift card.");
            return "redirect:/login?next=/gift-cards/buy";
        }

        BigDecimal amount = (amountPreset != null && amountPreset > 0)
                ? new BigDecimal(amountPreset)
                : customAmount;

        if (amount == null || amount.signum() <= 0) {
            ra.addFlashAttribute("flashOk", false);
            ra.addFlashAttribute("flashMsg", "Please choose a valid amount.");
            return "redirect:/gift-cards/buy";
        }

        Cart cart = getOrCreateCart(session);

        CartItem item = new CartItem();
        item.setType(CartItem.Type.GIFT_CARD);
        item.setProductId(-System.nanoTime());

        String niceDelivery = "email";
        if ("PHYSICAL".equalsIgnoreCase(delivery)) {
            niceDelivery = "physical";
        }

        item.setName("SpiceJar " + niceDelivery + " gift card");
        item.setQty(1);
        item.setPrice(amount);
        item.setImageUrl("/images/giftcard-1.png");
        item.setDescription("Gift card for " +
                (recipientName != null && !recipientName.isBlank()
                        ? recipientName
                        : "your recipient"));

        cart.getItems().add(item);
        session.setAttribute("CART", cart);

        ra.addFlashAttribute("flashOk", true);
        ra.addFlashAttribute("flashMsg", "Gift card added to your cart.");
        return "redirect:/cart/view";
    }
}
