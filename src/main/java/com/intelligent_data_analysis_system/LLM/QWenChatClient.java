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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QWenChatClient {
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${app.qwen.base-url}")
    private String baseUrl;

    @Value("${app.qwen.api-key}")
    private String apiKey;

    @Value("${app.qwen.model}")
    private String model;

    // 重试配置
    @Value("${app.qwen.max-retries:5}")
    private int maxRetries;

    @Value("${app.qwen.retry-delay-ms:1000}")
    private int retryDelayMs;

    @Value("${app.qwen.timeout-seconds:60}")
    private int timeoutSeconds;

    public String chat(List<Map<String, String>> messages) {
        int retryCount = 0;
        Exception lastException = null;

        // ✅ 每次请求的硬超时（建议 30~90 秒）
        Duration timeout = Duration.ofSeconds(timeoutSeconds);

        while (retryCount <= maxRetries) {
            try {
                ObjectNode body = objectMapper.createObjectNode();
                body.put("model", model);
                body.put("max_tokens", 2048);
                body.put("temperature", 0.0);
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
                        // ✅ 关键：block 加超时，避免永远挂住
                        .block(timeout);

                if (resp == null || resp.isBlank()) {
                    throw new RuntimeException("Empty response from QWen");
                }
                return extractContent(resp);

            } catch (Exception e) {
                lastException = e;
                retryCount++;

                if (retryCount <= maxRetries) {
                    try {
                        long sleepMs = (long) retryDelayMs * (1L << retryCount); // 指数退避
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        throw new RuntimeException("QWen API call failed after " + maxRetries + " retries", lastException);
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
                return "[QWen_ERROR] " + root.get("error").toString();
            }

            // 2) 正常 choices[0].message.content
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                return "[QWen_BAD_RESPONSE] " + resp;
            }

            JsonNode msg = choices.get(0).get("message");
            if (msg == null || msg.get("content") == null) {
                return "[QWen_BAD_RESPONSE] " + resp;
            }
            return msg.get("content").asText("");
        } catch (Exception e) {
            return "[QWen_PARSE_FAIL] " + resp;
        }
    }
}
