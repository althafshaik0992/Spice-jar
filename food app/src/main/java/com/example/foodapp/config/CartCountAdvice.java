// src/main/java/com/example/foodapp/web/CartCountAdvice.java
package com.example.foodapp.config;

import com.example.foodapp.util.Cart;
import com.example.foodapp.util.CartItem;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;


@ControllerAdvice
public class CartCountAdvice {

    @ModelAttribute
    public void addCartCount(Model model, HttpSession session) {
        int count = 0;
        Object c = session.getAttribute("CART");
        if (c instanceof Cart cart && cart.getItems() != null) {
            count = cart.getItems().stream().mapToInt(CartItem::getQty).sum();
        }
        model.addAttribute("cartCount", count);
    }
}
