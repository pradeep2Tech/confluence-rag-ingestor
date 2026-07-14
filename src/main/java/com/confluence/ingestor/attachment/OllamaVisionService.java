package com.confluence.ingestor.attachment;

import com.confluence.ingestor.config.AttachmentAnalysisProperties;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class OllamaVisionService {

    private static final Logger log = LoggerFactory.getLogger(OllamaVisionService.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final String VISION_PROMPT = """
            Analyze this Confluence attachment.

            Classify the image as exactly one of:

            - ui_screenshot
            - architecture_diagram
            - flowchart
            - sequence_diagram
            - table
            - photo
            - generic_image
            - other

            Extract:

            - title
            - purpose
            - visibleText
            - components
            - relationships
            - userActions
            - fields
            - warnings
            - searchableSummary
            - confidence

            Rules

            Do not hallucinate.

            Preserve visible technical names exactly.

            If text is unreadable say UNKNOWN.

            Architecture diagrams must identify:

            - services
            - databases
            - queues
            - APIs
            - boundaries
            - communication

            UI screenshots must identify:

            - screen name
            - controls
            - buttons
            - fields
            - actions

            Return valid JSON only.
            """;

    private final AttachmentAnalysisProperties properties;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    public OllamaVisionService(AttachmentAnalysisProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        Duration timeout = properties.timeout() != null ? properties.timeout() : Duration.ofSeconds(120);
        this.httpClient = new OkHttpClient.Builder()
                .callTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .connectTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .writeTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .build();
    }

    public VisionAnalysisResult analyzeImage(byte[] imageBytes, String fileName) throws IOException {
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.visionModel());
        body.put("stream", false);
        body.put("format", "json");
        body.put("messages", List.of(Map.of(
                "role", "user",
                "content", VISION_PROMPT,
                "images", List.of(base64))));

        String url = normalizeBaseUrl(properties.ollamaBaseUrl()) + "/api/chat";
        String jsonBody = objectMapper.writeValueAsString(body);
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(jsonBody, JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Ollama vision request failed: HTTP " + response.code());
            }
            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode message = root.path("message").path("content");
            if (message.isMissingNode() || message.asText().isBlank()) {
                throw new IOException("Ollama vision response missing message content");
            }
            String content = message.asText().strip();
            return objectMapper.readValue(content, VisionAnalysisResult.class);
        } catch (IOException ex) {
            log.warn("Vision analysis failed fileName={} error={}", fileName, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.warn("Vision analysis failed fileName={} error={}", fileName, ex.getMessage());
            throw new IOException(ex.getMessage(), ex);
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://localhost:11434";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
