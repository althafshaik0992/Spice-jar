// src/main/java/com/example/foodapp/web/AiChatController.java
package com.example.foodapp.controller;


import com.example.foodapp.Ai.AiChatService;
import com.example.foodapp.Ai.CartService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat")
public class AiChatController {




    private final  AiChatService chatService;

    private  final CartService cartService;



    @PostMapping
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> body,
                                                    HttpSession session) {

        // 🧠 Extract user message from the request
        String text = body.getOrDefault("text", "").trim();

        // ✅ Call your AiChatService with all 3 params
        var out = chatService.chat(session.getId(), text, session);

        // 🧾 Build the response in the shape expected by the frontend
        Map<String, Object> response = Map.of(
                "ok", true,
                "text", out.reply(),
                "cartCount", out.cartCount(),
                "actions", List.of(
                        Map.of("label", "Open cart", "url", "/cart/view"),
                        Map.of("label", "Checkout", "url", "/payment/checkout")
                )
        );

        return ResponseEntity.ok(response);
    }



}
