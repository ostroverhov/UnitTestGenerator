package com.example.unittestgenerator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Client for DeepSeek Chat Completions API.
 */
public class DeepSeekClient {

    private static final String DEFAULT_URL = "https://api.deepseek.com/chat/completions";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DeepSeekClient(String apiKey) {
        this.apiKey = apiKey;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Sends a chat completion request and returns the content of the first choice.
     *
     * @param model   DeepSeek model (e.g. "deepseek-chat")
     * @param messages list of messages: each is a map with "role" and "content"
     * @param maxTokens maximum tokens to generate
     * @return the text content of the assistant reply
     * @throws DeepSeekApiException on API or HTTP errors
     */
    public String chatCompletion(String model, List<Message> messages, int maxTokens) throws DeepSeekApiException {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("max_tokens", maxTokens);
            ArrayNode messagesArray = body.putArray("messages");
            for (Message msg : messages) {
                ObjectNode m = messagesArray.addObject();
                m.put("role", msg.getRole());
                m.put("content", msg.getContent());
            }
            String bodyJson = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(DEFAULT_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();

            if (statusCode < 200 || statusCode >= 300) {
                throw new DeepSeekApiException("DeepSeek API error: HTTP " + statusCode + " - " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new DeepSeekApiException("DeepSeek API returned no choices: " + response.body());
            }
            JsonNode first = choices.get(0);
            JsonNode message = first.path("message");
            JsonNode content = message.path("content");
            if (content.isMissingNode() || !content.isTextual()) {
                throw new DeepSeekApiException("DeepSeek API response missing message.content");
            }
            return content.asText();
        } catch (DeepSeekApiException e) {
            throw e;
        } catch (Exception e) {
            throw new DeepSeekApiException("Failed to call DeepSeek API: " + e.getMessage(), e);
        }
    }

    public static final class Message {
        private final String role;
        private final String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }
    }

    public static final class DeepSeekApiException extends RuntimeException {
        public DeepSeekApiException(String message) {
            super(message);
        }

        public DeepSeekApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
