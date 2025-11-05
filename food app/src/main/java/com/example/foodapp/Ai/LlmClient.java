package com.example.foodapp.Ai;

import java.util.List;
import java.util.Map;

public interface LlmClient {
    record ToolSpec(String name, String description, Map<String,Object> jsonSchema) {}
    record ToolCall(String name, Map<String,Object> args) {}
    record Result(String text, List<ToolCall> toolCalls) {}
    Result chat(String systemPrompt, List<Map<String,String>> history, List<ToolSpec> tools);
}
