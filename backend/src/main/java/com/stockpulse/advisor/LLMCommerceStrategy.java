package com.stockpulse.advisor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpulse.domain.ChangeDirection;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Component("aiPricing")
public class LLMCommerceStrategy implements PricingStrategy {
    private static final Logger log = LoggerFactory.getLogger(LLMCommerceStrategy.class);
    private static final String REASONING_MARKER = "===JSON===";
    private final LLMGateway gateway; private final RuleBasedPricingStrategy fallback; private final ObjectMapper mapper = new ObjectMapper();
    public LLMCommerceStrategy(LLMGateway gateway, RuleBasedPricingStrategy fallback) { this.gateway = gateway; this.fallback = fallback; }
    public PricingRecommendation recommend(CommerceContext context) {
        try {
            var node = mapper.readTree(extract(gateway.callLLM(prompt(context))));
            var price = new BigDecimal(node.path("recommendedPrice").asText());
            var direction = ChangeDirection.valueOf(node.path("direction").asText("HOLD"));
            var confidence = node.path("confidence").asDouble();
            var reasoning = node.path("reasoning").asText("").trim();
            if (price.signum() <= 0 || price.compareTo(context.product().getCurrentPrice().multiply(BigDecimal.TEN)) > 0 || !Double.isFinite(confidence) || confidence < 0 || confidence > 1 || reasoning.isBlank()) throw new IllegalArgumentException("Invalid AI pricing response");
            return new PricingRecommendation(price, direction, confidence, reasoning);
        } catch (Exception exception) {
            log.warn("AI pricing unavailable; using rules fallback: {}", exception.getMessage());
            return fallback.recommend(context);
        }
    }
    /** Streams the model's live reasoning tokens to onToken as they are generated, then parses the trailing JSON block. Falls back to rules on any failure. */
    public PricingRecommendation streamRecommend(CommerceContext context, Consumer<String> onToken) {
        try {
            var raw = new StringBuilder();
            var emitted = new int[]{0};
            var markerAt = new int[]{-1};
            var content = gateway.streamLLM(streamingPrompt(context), delta -> {
                raw.append(delta);
                if (markerAt[0] < 0) markerAt[0] = raw.indexOf(REASONING_MARKER);
                var safeEnd = markerAt[0] >= 0 ? markerAt[0] : Math.max(emitted[0], raw.length() - REASONING_MARKER.length() + 1);
                if (safeEnd > emitted[0]) { onToken.accept(raw.substring(emitted[0], safeEnd)); emitted[0] = safeEnd; }
            });
            return parseStreamed(content, context);
        } catch (Exception exception) {
            log.warn("Streaming AI pricing unavailable; using rules fallback: {}", exception.getMessage());
            return fallback.recommend(context);
        }
    }
    String prompt(CommerceContext context) { return "Return JSON only with recommendedPrice, direction, confidence, reasoning. " + guidance(context) + productLine(context); }
    String streamingPrompt(CommerceContext context) { return "First, write 2-4 plain-English sentences explaining the pricing decision (no markdown, no JSON). Then on a new line write exactly " + REASONING_MARKER + " followed immediately by a single-line compact JSON object with keys recommendedPrice (number), direction (INCREASE, DECREASE, or HOLD), confidence (0 to 1). Do not repeat the reasoning inside the JSON object. " + guidance(context) + productLine(context); }
    private String guidance(CommerceContext context) { return switch (context.triggerReason()) { case INVENTORY_LOW -> "Inventory is below threshold. Protect remaining availability without making an unjustified jump; explain the tradeoff between scarcity protection and clearing slow stock."; case DEMAND_SPIKE -> "Demand has spiked versus category peers. Capture willingness to pay with a modest change while considering conversion and customer trust."; case MANUAL -> "This is a manual review. Balance inventory health, demand, and customer value before recommending a change."; default -> "Use the observed commerce signal and explain the merchandising tradeoff."; }; }
    private String productLine(CommerceContext context) { var p = context.product(); return " Product=" + p.getName() + ", category=" + p.getCategory() + ", price=" + p.getCurrentPrice() + ", stock=" + p.getStockLevel() + ", threshold=" + p.getReorderThreshold() + ", velocity=" + p.getDemandVelocity() + ", categoryAverage=" + context.categoryAverageVelocity() + ", trigger=" + context.triggerReason() + "."; }
    private PricingRecommendation parseStreamed(String content, CommerceContext context) throws Exception {
        var markerIndex = content.indexOf(REASONING_MARKER);
        if (markerIndex < 0) throw new IllegalArgumentException("Missing JSON marker in streamed AI response");
        var reasoning = content.substring(0, markerIndex).trim();
        var node = mapper.readTree(content.substring(markerIndex + REASONING_MARKER.length()).trim());
        var price = new BigDecimal(node.path("recommendedPrice").asText());
        var direction = ChangeDirection.valueOf(node.path("direction").asText("HOLD"));
        var confidence = node.path("confidence").asDouble();
        if (price.signum() <= 0 || price.compareTo(context.product().getCurrentPrice().multiply(BigDecimal.TEN)) > 0 || !Double.isFinite(confidence) || confidence < 0 || confidence > 1 || reasoning.isBlank()) throw new IllegalArgumentException("Invalid streamed AI pricing response");
        return new PricingRecommendation(price, direction, confidence, reasoning);
    }
    static String extract(String raw) throws Exception { var mapper = new ObjectMapper(); var root = mapper.readTree(raw); if (root.has("response")) return root.get("response").asText(); if (root.has("choices")) return root.get("choices").get(0).get("message").get("content").asText(); if (root.has("candidates")) return root.get("candidates").get(0).get("content").get("parts").get(0).get("text").asText(); return raw; }
}