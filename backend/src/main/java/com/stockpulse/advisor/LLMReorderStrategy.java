package com.stockpulse.advisor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Component("aiReorder")
public class LLMReorderStrategy implements ReorderStrategy {
    private static final Logger log = LoggerFactory.getLogger(LLMReorderStrategy.class);
    private final LLMGateway gateway; private final RuleBasedReorderStrategy fallback; private final ObjectMapper mapper = new ObjectMapper();
    public LLMReorderStrategy(LLMGateway gateway, RuleBasedReorderStrategy fallback) { this.gateway = gateway; this.fallback = fallback; }
    public ReorderRecommendation recommend(CommerceContext context) {
        try {
            var node = mapper.readTree(LLMCommerceStrategy.extract(gateway.callLLM(prompt(context))));
            int quantity = node.path("recommendedQuantity").asInt();
            int leadTime = node.path("suggestedLeadTimeDays").asInt(7);
            double confidence = node.path("confidence").asDouble();
            var reasoning = node.path("reasoning").asText("").trim();
            if (quantity < 1 || leadTime < 1 || leadTime > 365 || !Double.isFinite(confidence) || confidence < 0 || confidence > 1 || reasoning.isBlank()) throw new IllegalArgumentException("Invalid AI reorder response");
            return new ReorderRecommendation(quantity, leadTime, confidence, reasoning);
        } catch (Exception exception) {
            log.warn("AI reorder unavailable; using rules fallback: {}", exception.getMessage());
            return fallback.recommend(context);
        }
    }
    private String prompt(CommerceContext context) { var p = context.product(); var guidance = switch (context.triggerReason()) { case INVENTORY_LOW -> "Stock is below threshold. Size the inbound shipment to restore service without overbuying; account for current shortage and lead time."; case DEMAND_SPIKE -> "Velocity is accelerating. Size replenishment for the elevated run rate and explain the risk of stockout versus excess inventory."; case MANUAL -> "This is a manual replenishment review. Recommend a positive integer based on threshold, current stock, velocity, and lead time."; default -> "Use the observed commerce signal to size replenishment."; }; return "Return JSON only with recommendedQuantity, suggestedLeadTimeDays, confidence, reasoning. " + guidance + " Product=" + p.getName() + ", category=" + p.getCategory() + ", stock=" + p.getStockLevel() + ", threshold=" + p.getReorderThreshold() + ", velocity=" + p.getDemandVelocity() + ", categoryAverage=" + context.categoryAverageVelocity() + ", trigger=" + context.triggerReason() + "."; }
}