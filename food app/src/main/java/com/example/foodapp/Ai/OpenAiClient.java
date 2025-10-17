// src/main/java/com/example/foodapp/Ai/OpenAiClient.java
package com.example.foodapp.Ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Minimal OpenAI Chat Completions client.
 * - Uses GPT-4o-mini by default (adjust the model if you like).
 * - Builds messages from a simple [{role, content}] map list.
 * - Supports "function" tools (tool_calls) and parses their JSON arguments.
 */
@Component
public class OpenAiClient implements LlmClient {

    @Value("${openai.api.key:}")
    private String apiKey;

    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper json = new ObjectMapper();

    @Override
    public Result chat(String systemPrompt,
                       List<Map<String, String>> history,
                       List<ToolSpec> tools) {

        // ----- Build request body -----
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "gpt-4o-mini");

        // messages
        List<Map<String, Object>> msgs = new ArrayList<>();
        msgs.add(Map.of("role", "system", "content", systemPrompt));
        if (history != null) {
            for (Map<String, String> m : history) {
                if (m == null) continue;
                String role = m.getOrDefault("role", "user");
                String content = m.getOrDefault("content", "");
                msgs.add(Map.of("role", role, "content", content));
            }
        }
        body.put("messages", msgs);

        // tools (as OpenAI "function" tools)
        if (tools != null && !tools.isEmpty()) {
            List<Map<String, Object>> toolDefs = new ArrayList<>();
            for (ToolSpec t : tools) {
                if (t == null) continue;
                Map<String, Object> fn = new LinkedHashMap<>();
                fn.put("name", t.name());
                fn.put("description", t.description());
                // jsonSchema() should already be a Map (JSON schema)
                fn.put("parameters", t.jsonSchema());

                toolDefs.add(Map.of("type", "function", "function", fn));
            }
            body.put("tools", toolDefs);
            body.put("tool_choice", "auto");
        }

        // ----- HTTP request -----
        HttpHeaders h = new HttpHeaders();
        h.setAccept(List.of(MediaType.APPLICATION_JSON));
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(apiKey);
        h.set("OpenAI-Beta", "assistants=v2");

        try {
            ResponseEntity<Map> res = http.exchange(
                    "https://api.openai.com/v1/chat/completions",
                    HttpMethod.POST,
                    new HttpEntity<>(body, h),
                    Map.class
            );

            Map<?, ?> resBody = res.getBody() == null ? Map.of() : res.getBody();

            // ----- Safely unwrap choices[0].message -----
            Object choicesObj = resBody.get("choices");
            List<?> choices = (choicesObj instanceof List<?> l) ? l : List.of();

            Map<?, ?> choice = choices.isEmpty() ? Map.of() :
                    (choices.get(0) instanceof Map<?, ?> c0 ? c0 : Map.of());

            Object messageObj = choice.get("message");
            Map<?, ?> message = (messageObj instanceof Map<?, ?> m) ? m : Map.of();

            String text = Objects.toString(message.get("content"), "");

            // ----- Parse optional tool_calls -----
            List<ToolCall> toolCallsOut = new ArrayList<>();
            Object toolCallsObj = message.get("tool_calls");
            List<?> toolCalls = (toolCallsObj instanceof List<?> l) ? l : List.of();

            for (Object o : toolCalls) {
                if (!(o instanceof Map<?, ?> tc)) continue;
                Object fnObj = tc.get("function");
                if (!(fnObj instanceof Map<?, ?> fn)) continue;

                String name = Objects.toString(fn.get("name"), "");
                String argStr = Objects.toString(fn.get("arguments"), "");

                Map<String, Object> args = Map.of();
                if (!argStr.isBlank() && !"null".equalsIgnoreCase(argStr)) {
                    try {
                        args = json.readValue(argStr, new TypeReference<Map<String, Object>>() {
                        });
                    } catch (Exception ignore) {
                        // leave args empty if parsing fails
                    }
                }
                toolCallsOut.add(new ToolCall(name, args));
            }

            return new Result(text, toolCallsOut);
        } catch (HttpStatusCodeException e) {
            System.err.println("❌ OpenAI error " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
            return new Result("I’m having trouble connecting to OpenAI (" + e.getStatusCode() + ").", List.of());
        } catch (Exception e) {
            e.printStackTrace();
            return new Result("I’m having trouble connecting to OpenAI.", List.of());
        }
    }
}
