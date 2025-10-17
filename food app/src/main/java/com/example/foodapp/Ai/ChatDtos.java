package com.example.foodapp.Ai;

import java.util.List;
import java.util.Map;

public class ChatDtos {

    public record ChatRequest(String sessionId, String message) {}

    public record ChatMessage(String role, String content) { // "user" | "assistant" | "tool"
        public static ChatMessage user(String c){ return new ChatMessage("user", c); }
        public static ChatMessage assistant(String c){ return new ChatMessage("assistant", c); }
        public static ChatMessage tool(String c){ return new ChatMessage("tool", c); }
    }

    public record ToolCall(String name, Map<String,Object> arguments) {}
    public record ChatToolResult(String name, Object result) {}

    public record ChatResponse(
            String reply,                           // assistant text
            List<Map<String,Object>> suggestions,   // quick reply chips
            String checkoutUrl                      // if checkout started
    ) {}

}
