package com.example.foodapp.util;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

@Component
@SessionScope
public class CartSession {
    private Cart cart = new Cart();
    public Cart get(){ return cart; }
}
