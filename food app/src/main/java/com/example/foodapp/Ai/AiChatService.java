package com.example.foodapp.Ai;

import com.example.foodapp.model.Product;
import com.example.foodapp.service.ProductService;
import com.example.foodapp.service.StripeService;
import com.example.foodapp.service.PaypalService;
import com.example.foodapp.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiChatService {

    private final LlmClient llm;                 // OpenAiClient bean
    private final ProductService productService;
    private final CartService cartService;
    private final StripeService stripeService;   // optional future
    private final PaypalService paypalService;   // optional future
    private final PaymentService paymentService; // optional future

    public record ChatReply(String reply, int cartCount) {}

    private static final String SESSION_HISTORY = "AI_HISTORY";

    public ChatReply chat(String sessionId, String userText, HttpSession session) {
        if (userText == null || userText.isBlank()) {
            return new ChatReply("Tell me what you need — e.g. “find cumin”, “add turmeric 2”, or “checkout”.", cartQty(sessionId));
        }

        // 1) Load short history (assistant/user messages only)
        List<Map<String,String>> history = loadHistory(session);

        // 2) Build tools the model can call
        List<LlmClient.ToolSpec> tools = List.of(
                new LlmClient.ToolSpec(
                        "searchProducts",
                        "Search catalog by free text. Return up to N matches.",
                        Map.of(
                                "type","object",
                                "properties", Map.of(
                                        "query", Map.of("type","string", "description","keywords e.g. 'cumin' or 'turmeric'"),
                                        "limit", Map.of("type","integer","minimum",1,"maximum",12,"default",6)
                                ),
                                "required", List.of("query")
                        )
                ),
                new LlmClient.ToolSpec(
                        "addToCart",
                        "Add a product to cart by productId and quantity (1-99).",
                        Map.of(
                                "type","object",
                                "properties", Map.of(
                                        "productId", Map.of("type","integer"),
                                        "qty", Map.of("type","integer","minimum",1,"maximum",99,"default",1)
                                ),
                                "required", List.of("productId")
                        )
                ),
                new LlmClient.ToolSpec(
                        "cartItems",
                        "List current cart items.",
                        Map.of("type","object","properties", Map.of(), "required", List.of())
                ),
                new LlmClient.ToolSpec(
                        "checkoutUrl",
                        "Return the URL where user can finish payment.",
                        Map.of("type","object","properties", Map.of(), "required", List.of())
                )
        );

        // 3) Ask the LLM
        var result = llm.chat(systemPrompt(), append(history, Map.of("role","user","content", userText)), tools);

        // 4) If the model requested tools, execute them and re-ask once with results
        if (result.toolCalls() != null && !result.toolCalls().isEmpty()) {
            List<Map<String,String>> toolResponses = new ArrayList<>();
            for (var call : result.toolCalls()) {
                toolResponses.add(executeTool(sessionId, call));
            }

            // Feed tool outputs back to the model
            List<Map<String,String>> withTools = new ArrayList<>(history);
            withTools.add(Map.of("role","user","content", userText));
            toolResponses.forEach(withTools::add);

            result = llm.chat(systemPrompt(), withTools, tools);
        }

        String reply = (result.text() == null || result.text().isBlank())
                ? "I’m here to help! Try “find turmeric”, “add cumin 2”, or “checkout”."
                : result.text();

        // 5) Save trimmed history
        saveHistory(session, trim(append(history,
                Map.of("role","user","content", userText),
                Map.of("role","assistant","content", reply)
        ), 12));

        return new ChatReply(reply, cartQty(sessionId));
    }

    // ---- tools executor ----
    private Map<String,String> executeTool(String sessionId, LlmClient.ToolCall call) {
        try {
            switch (call.name()) {
                case "searchProducts" -> {
                    String q = String.valueOf(call.args().getOrDefault("query",""));
                    int limit = ((Number) call.args().getOrDefault("limit", 6)).intValue();
                    var found = productService.search(q, limit);
                    String text = found.isEmpty()
                            ? "No products found."
                            : found.stream()
                            .map(p -> p.getId()+": "+p.getName()+" — $"+fmt(p.getPrice()))
                            .collect(Collectors.joining("\n"));
                    return Map.of("role","tool", "name","searchProducts", "content", text);
                }
                case "addToCart" -> {
                    long productId = ((Number) call.args().get("productId")).longValue();
                    int qty = ((Number) call.args().getOrDefault("qty", 1)).intValue();
                    cartService.add(sessionId, productId, Math.max(1, Math.min(99, qty)));
                    int count = cartQty(sessionId);
                    return Map.of("role","tool", "name","addToCart", "content", "OK (cart items: "+count+")");
                }
                case "cartItems" -> {
                    var lines = cartService.items(sessionId);
                    String content = lines.isEmpty()
                            ? "Cart is empty."
                            : lines.stream()
                            .map(l -> l.qty()+" × "+l.productName()+" — $"+fmt(l.lineTotal()))
                            .collect(Collectors.joining("\n"));
                    return Map.of("role","tool","name","cartItems","content", content);
                }
                case "checkoutUrl" -> {
                    // If you need to freeze to an order and redirect, do that here:
                    // Long orderId = cartService.freezeToOrder(sessionId);
                    // String url = "/payment/checkout?orderId=" + orderId;
                    String url = "/payment/checkout";
                    return Map.of("role","tool","name","checkoutUrl","content", url);
                }
            }
        } catch (Exception e) {
            return Map.of("role","tool","name",call.name(),"content","ERROR: "+e.getMessage());
        }
        return Map.of("role","tool","name",call.name(),"content","UNKNOWN_TOOL");
    }

    // ---- helpers ----
    private int cartQty(String sessionId) {
        try {
            return cartService.items(sessionId).stream().mapToInt(CartService.CartLine::qty).sum();
        } catch (Exception e) { return 0; }
    }
    private static String fmt(BigDecimal b) { return b==null? "0.00" : b.setScale(2, BigDecimal.ROUND_HALF_UP).toString(); }

    private static List<Map<String,String>> loadHistory(HttpSession s) {
        Object o = s.getAttribute(SESSION_HISTORY);
        if (o instanceof List<?> l) return (List<Map<String,String>>) l;
        return new ArrayList<>();
    }
    private static void saveHistory(HttpSession s, List<Map<String,String>> h) { s.setAttribute(SESSION_HISTORY, h); }
    private static List<Map<String,String>> append(List<Map<String,String>> base, Map<String,String>... add) {
        List<Map<String,String>> out = new ArrayList<>(base); out.addAll(Arrays.asList(add)); return out;
    }
    private static List<Map<String,String>> trim(List<Map<String,String>> h, int max) {
        return h.size() <= max ? h : h.subList(h.size()-max, h.size());
    }

    private static String systemPrompt() {
        return """
        You are Spice AI for an online spice shop. Be concise, friendly and helpful.
        You can call tools to search products, add to cart, show the cart and give a checkout URL.
        When you mention products, include name and price. Prefer tool results over guessing.
        If a user asks to pay, recommend /payment/checkout and call checkoutUrl tool.
        """;
    }
}
