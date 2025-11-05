package com.yourapp.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ChatController {

    @Value("${openai.api.key:${OPENAI_API_KEY:}}")
    private String openAiApiKey;

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    // Model name per your org; adjust if your account uses a different default:
    private static final String MODEL = "gpt-4o-mini";

    record ChatRequest(String message) {}
    record ChatResponse(String reply) {}

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest req) {
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ChatResponse("Server is missing an OpenAI API key."));
        }
        String userMsg = (req != null && req.message()!=null) ? req.message().trim() : "";
        if (userMsg.isEmpty()) return ResponseEntity.ok(new ChatResponse(""));

        // Build OpenAI payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", MODEL);
        payload.put("messages", List.of(
                Map.of("role","system","content","You are SpiceBot, a friendly assistant for The Spice Jar e-commerce site. Be concise and helpful. If asked about an order, politely ask for the order id."),
                Map.of("role","user","content", userMsg)
        ));
        payload.put("temperature", 0.3);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiApiKey);

        HttpEntity<Map<String,Object>> entity = new HttpEntity<>(payload, headers);
        RestTemplate rt = new RestTemplate();
        Map<?,?> res = rt.postForObject(OPENAI_URL, entity, Map.class);

        String reply = extractReply(res);
        return ResponseEntity.ok(new ChatResponse(reply));
    }

    @SuppressWarnings("unchecked")
    private String extractReply(Map<?,?> res){
        try{
            var choices = (List<Map<String,Object>>) res.get("choices");
            if(choices==null || choices.isEmpty()) return "I couldn’t find a response.";
            var msg = (Map<String,Object>) choices.get(0).get("message");
            var content = (String) msg.get("content");
            return (content==null || content.isBlank()) ? "I couldn’t find a response." : content.trim();
        }catch(Exception e){
            return "I had trouble reading the AI response.";
        }
    }
}
