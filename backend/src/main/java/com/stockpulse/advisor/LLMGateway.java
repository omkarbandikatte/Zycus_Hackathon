package com.stockpulse.advisor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
@Component
public class LLMGateway {
    private static final Logger log = LoggerFactory.getLogger(LLMGateway.class);
    private final RestClient http;
    private final HttpClient streamHttp = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    @Value("${llm.provider:ollama}") private String provider;
    @Value("${llm.api-key:}") private String apiKey;
    @Value("${llm.model:llama3.1}") private String model;
    @Value("${llm.base-url:http://localhost:11434}") private String baseUrl;
    @Value("${llm.backup.base-url:}") private String backupBaseUrl;
    @Value("${llm.backup.api-key:}") private String backupApiKey;
    @Value("${llm.backup.model:}") private String backupModel;
    @Value("${llm.backup.path:/chat/completions}") private String backupPath;
    @Value("${llm.backup.product:}") private String backupProduct;
    @Value("${llm.backup.flowname:}") private String backupFlowname;
    @Value("${llm.backup.bundlename:}") private String backupBundlename;
    @Value("${llm.backup.user-id:}") private String backupUserId;
    @Value("${llm.backup.tenant-id:}") private String backupTenantId;
    @Value("${llm.backup.execution-mode:manual}") private String backupExecutionMode;
    private final int streamTimeoutSeconds;
    public LLMGateway(@Value("${llm.connect-timeout-seconds:10}") int connectTimeoutSeconds, @Value("${llm.read-timeout-seconds:45}") int readTimeoutSeconds) { var requestFactory = new SimpleClientHttpRequestFactory(); requestFactory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds)); requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds)); http = RestClient.builder().requestFactory(requestFactory).build(); streamTimeoutSeconds = readTimeoutSeconds; }
    public String callLLM(String prompt) {
        try {
            return callPrimary(prompt);
        } catch (Exception primaryFailure) {
            if (!hasBackup()) throw primaryFailure;
            log.warn("Primary LLM provider '{}' failed; falling back to backup provider: {}", provider, primaryFailure.getMessage());
            return callBackup(prompt);
        }
    }
    private String callPrimary(String prompt) {
        if ("ollama".equalsIgnoreCase(provider)) return http.post().uri(baseUrl + "/api/generate").body(Map.of("model", model, "prompt", prompt, "stream", false)).retrieve().body(String.class);
        if ("groq".equalsIgnoreCase(provider)) return http.post().uri(baseUrl + "/openai/v1/chat/completions").header("Authorization", "Bearer " + apiKey).body(Map.of("model", model, "messages", List.of(Map.of("role", "user", "content", prompt)), "temperature", 0.1)).retrieve().body(String.class);
        if ("gemini".equalsIgnoreCase(provider)) return http.post().uri(baseUrl + "/v1beta/models/" + model + ":generateContent?key=" + apiKey).body(Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))))).retrieve().body(String.class);
        throw new IllegalStateException("Unknown LLM provider: " + provider);
    }
    private String callBackup(String prompt) {
        var request = http.post().uri(backupBaseUrl + backupPath).header("Authorization", "Bearer " + backupApiKey);
        backupHeaders().forEach(request::header);
        return request.body(Map.of("model", backupModel, "messages", List.of(Map.of("role", "user", "content", prompt)), "temperature", 0.1)).retrieve().body(String.class);
    }
    private boolean hasBackup() { return backupBaseUrl != null && !backupBaseUrl.isBlank(); }
    private Map<String, String> backupHeaders() {
        var headers = new LinkedHashMap<String, String>();
        if (!backupProduct.isBlank()) headers.put("product", backupProduct);
        if (!backupFlowname.isBlank()) headers.put("flowname", backupFlowname);
        if (!backupBundlename.isBlank()) headers.put("bundlename", backupBundlename);
        if (!backupUserId.isBlank()) headers.put("x-zycus-userid", backupUserId);
        if (!backupTenantId.isBlank()) headers.put("x-zycus-tenantid", backupTenantId);
        if (!backupExecutionMode.isBlank()) headers.put("x-zycus-execution-mode", backupExecutionMode);
        return headers;
    }
    /** Extracts the plain-text reply from a provider envelope (Ollama, Groq/OpenAI, Gemini). */
    public static String extractText(String raw) throws Exception {
        var root = new ObjectMapper().readTree(raw);
        if (root.has("response")) return root.get("response").asText();
        if (root.has("choices")) return root.get("choices").get(0).get("message").get("content").asText();
        if (root.has("candidates")) return root.get("candidates").get(0).get("content").get("parts").get(0).get("text").asText();
        return raw;
    }
    /** Streams the completion live, invoking onToken per delta as the provider generates it, and returns the full concatenated text. Falls back to the backup provider if the primary stream fails to start. */
    public String streamLLM(String prompt, Consumer<String> onToken) throws Exception {
        try {
            return streamPrimary(prompt, onToken);
        } catch (Exception primaryFailure) {
            if (!hasBackup()) throw primaryFailure;
            log.warn("Primary LLM stream '{}' failed; falling back to backup provider: {}", provider, primaryFailure.getMessage());
            return streamOpenAiCompatible(prompt, backupBaseUrl + backupPath, backupApiKey, backupModel, backupHeaders(), onToken);
        }
    }
    private String streamPrimary(String prompt, Consumer<String> onToken) throws Exception {
        if ("ollama".equalsIgnoreCase(provider)) return streamOllama(prompt, onToken);
        if ("groq".equalsIgnoreCase(provider)) return streamOpenAiCompatible(prompt, baseUrl + "/openai/v1/chat/completions", apiKey, model, Map.of(), onToken);
        if ("gemini".equalsIgnoreCase(provider)) return streamGemini(prompt, onToken);
        throw new IllegalStateException("Unknown LLM provider: " + provider);
    }
    private String streamOllama(String prompt, Consumer<String> onToken) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/generate"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(streamTimeoutSeconds))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("model", model, "prompt", prompt, "stream", true))))
            .build();
        var response = streamHttp.send(request, HttpResponse.BodyHandlers.ofInputStream());
        var full = new StringBuilder();
        try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                var node = mapper.readTree(line);
                var delta = node.path("response").asText("");
                if (!delta.isEmpty()) { full.append(delta); onToken.accept(delta); }
                if (node.path("done").asBoolean(false)) break;
            }
        }
        return full.toString();
    }
    private String streamOpenAiCompatible(String prompt, String url, String bearerKey, String modelId, Map<String, String> extraHeaders, Consumer<String> onToken) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + bearerKey);
        builder.timeout(Duration.ofSeconds(streamTimeoutSeconds));
        extraHeaders.forEach(builder::header);
        var request = builder.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("model", modelId, "messages", List.of(Map.of("role", "user", "content", prompt)), "temperature", 0.1, "stream", true)))).build();
        var response = streamHttp.send(request, HttpResponse.BodyHandlers.ofInputStream());
        var full = new StringBuilder();
        try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                var payload = line.substring(5).trim();
                if (payload.isEmpty() || payload.equals("[DONE]")) continue;
                var node = mapper.readTree(payload);
                var delta = node.path("choices").path(0).path("delta").path("content").asText("");
                if (!delta.isEmpty()) { full.append(delta); onToken.accept(delta); }
            }
        }
        return full.toString();
    }
    private String streamGemini(String prompt, Consumer<String> onToken) throws Exception {
        var url = baseUrl + "/v1beta/models/" + model + ":streamGenerateContent?alt=sse&key=" + apiKey;
        var request = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(streamTimeoutSeconds))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))))))
            .build();
        var response = streamHttp.send(request, HttpResponse.BodyHandlers.ofInputStream());
        var full = new StringBuilder();
        try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                var payload = line.substring(5).trim();
                if (payload.isEmpty()) continue;
                var node = mapper.readTree(payload);
                var delta = node.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
                if (!delta.isEmpty()) { full.append(delta); onToken.accept(delta); }
            }
        }
        return full.toString();
    }
}