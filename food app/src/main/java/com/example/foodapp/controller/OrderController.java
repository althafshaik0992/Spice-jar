// src/main/java/com/example/foodapp/controller/OrderController.java
package com.example.foodapp.controller;

import com.example.foodapp.Ai.CartService;
import com.example.foodapp.model.Address;
import com.example.foodapp.model.Order;
import com.example.foodapp.model.OrderItem;
import com.example.foodapp.model.User;
import com.example.foodapp.service.AddressService;
import com.example.foodapp.service.EmailService;
import com.example.foodapp.service.InventoryService;
import com.example.foodapp.service.OrderService;
import com.example.foodapp.util.Cart;
import com.example.foodapp.util.CartItem;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/order")
public class OrderController extends BaseController {

    private final OrderService orderService;
    private final AddressService addressService;

    private final InventoryService inventoryService;

    private final EmailService emailService;

    public OrderController(OrderService orderService, AddressService addressService, InventoryService inventoryService, EmailService emailService) {
        this.orderService = orderService;
        this.addressService = addressService;
        this.inventoryService = inventoryService;
        this.emailService = emailService;
    }

    @GetMapping("/checkout")
    public String checkout(Model m, HttpSession session) {
        User user = currentUser(session);
        if (user == null) return "redirect:/login";

        Cart cart = (Cart) session.getAttribute("CART");
        if (cart == null || cart.isEmpty()) return "redirect:/cart/view";

        m.addAttribute("currentUser", user);
        m.addAttribute("addresses", addressService.listForUser(user));
        m.addAttribute("cart", cart);
        return "checkout";
    }

    @PostMapping("/place")
    public String place(@RequestParam(required = false) Long addressId,
                        @RequestParam String customerName,
                        @RequestParam String email,
                        @RequestParam String phone,
                        @RequestParam String street,
                        @RequestParam String city,
                        @RequestParam String state,
                        @RequestParam String zip,
                        @RequestParam String country,
                        HttpSession session) {
        User user = currentUser(session);
        if (user == null) return "redirect:/login";

        Cart cart = (Cart) session.getAttribute("CART");
        if (cart == null || cart.isEmpty()) return "redirect:/cart/view";

        // override with saved address (defensive check)
        if (addressId != null) {
            Address a = addressService.findById(addressId).orElse(null);
            if (a != null && a.getUser() != null && a.getUser().getId().equals(user.getId())) {
                customerName = nvl(a.getFullName(), customerName);
                phone = nvl(a.getPhone(), phone);
                String fullStreet = joinNonBlank(a.getLine1(), a.getLine2());
                street = nvl(fullStreet, street);
                street = nvl(a.getLine1(), street);
                city = nvl(a.getCity(), city);
                state = nvl(a.getState(), state);
                zip = nvl(a.getZip(), zip);
                country = nvl(a.getCountry(), country);
            }
        }

        Order o = new Order();
        o.setCreatedAt(LocalDateTime.now());
        o.setUserId(user.getId());
        o.setCustomerName(customerName);
        o.setEmail(email);
        o.setPhone(phone);
        o.setStreet(street);
        o.setCity(city);
        o.setState(state);
        o.setZip(zip);
        o.setCountry(country);
        o.setConfirmationNumber(orderService.generateUniqueConfirmationNumber());
        o.setItems(cart.getItems().stream().map(ci -> {
            OrderItem it = new OrderItem();
            it.setProductId(ci.getProductId());
            it.setProductName(ci.getName());
            it.setQuantity(ci.getQty());
            it.setPrice(ci.getPrice());
            it.setImageUrl(ci.getImageUrl());
            it.setLineTotal(ci.getPrice()
                    .multiply(BigDecimal.valueOf(ci.getQty()))
                    .setScale(2, RoundingMode.HALF_UP));
            return it;
        }).collect(Collectors.toList()));

        BigDecimal subtotal = nz(cart.getSubtotal());
        if (subtotal.signum() == 0) {
            subtotal = cart.getItems().stream().map(CartItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        BigDecimal tax = subtotal.multiply(new BigDecimal("0.08")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal grand = subtotal.add(tax).setScale(2, RoundingMode.HALF_UP);


        o.setStatus("PENDING_PAYMENT");


        orderService.save(o);
        cart.clear();

        emailService.sendOrderConfirmation(o.getId());


        // ⬇️ decrement inventory immediately (or after payment success if you prefer)
        inventoryService.applyOrder(o);


        return "redirect:/payment/checkout?orderId=" + o.getId();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String nvl(String v, String fb) {
        return (v == null || v.isBlank()) ? fb : v;
    }


    private static String joinNonBlank(String... parts) {
        return java.util.Arrays.stream(parts)
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.joining(" "));
    }
}
