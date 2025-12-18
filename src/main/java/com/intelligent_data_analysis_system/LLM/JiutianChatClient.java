package com.intelligent_data_analysis_system.LLM;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JiutianChatClient {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${app.jiutian.base-url}")
    private String baseUrl;

    @Value("${app.jiutian.api-key}")
    private String apiKey;

    @Value("${app.jiutian.model}")
    private String model;

    public String chat(List<Map<String, String>> messages) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("max_tokens", 2048);
            body.put("temperature", 0.0); // text2sql 强烈建议 0
            body.put("stream", false);

            ArrayNode msgs = body.putArray("messages");
            for (Map<String, String> m : messages) {
                ObjectNode n = msgs.addObject();
                n.put("role", m.get("role"));
                n.put("content", m.get("content"));
            }

            String resp = webClientBuilder.build()
                    .post()
                    .uri(baseUrl + "/v1/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return extractContent(resp);

        } catch (Exception e) {
            throw new RuntimeException("Jiutian API call failed", e);
        }
    }

    public String chat(String systemContent, String userContent) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemContent == null ? "" : systemContent));
        messages.add(Map.of("role", "user", "content", userContent == null ? "" : userContent));
        return chat(messages); // 复用你原来的 chat(List<...>)
    }


    /** 从 OpenAI 风格响应中提取 assistant content */
    private String extractContent(String resp) {
        try {
            JsonNode root = objectMapper.readTree(resp);

            // 1) 先处理错误
            if (root.has("error")) {
                return "[JIUTIAN_ERROR] " + root.get("error").toString();
            }

            // 2) 正常 choices[0].message.content
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                return "[JIUTIAN_BAD_RESPONSE] " + resp;
            }

            JsonNode msg = choices.get(0).get("message");
            if (msg == null || msg.get("content") == null) {
                return "[JIUTIAN_BAD_RESPONSE] " + resp;
            }
            return msg.get("content").asText("");
        } catch (Exception e) {
            return "[JIUTIAN_PARSE_FAIL] " + resp;
        }
    }

}
