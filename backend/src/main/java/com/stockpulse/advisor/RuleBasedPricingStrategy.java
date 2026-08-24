package com.stockpulse.advisor;
import com.stockpulse.domain.ChangeDirection;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;
@Component("rulesPricing")
public class RuleBasedPricingStrategy implements PricingStrategy {
    public PricingRecommendation recommend(CommerceContext context) {
        var product = context.product();
        if (product.getStockLevel() < product.getReorderThreshold()) return increase(product.getCurrentPrice(), 0.10, 0.88, "Stock is below the reorder threshold; a measured increase protects remaining availability.");
        if (context.categoryAverageVelocity() > 0 && product.getDemandVelocity() > context.categoryAverageVelocity() * 2) return increase(product.getCurrentPrice(), 0.05, 0.82, "Demand velocity is more than twice the category average; a modest increase captures the signal.");
        return new PricingRecommendation(product.getCurrentPrice(), ChangeDirection.HOLD, 0.72, "Inventory and demand are within the configured guardrails; hold the current price.");
    }
    private PricingRecommendation increase(BigDecimal price, double rate, double confidence, String reasoning) { return new PricingRecommendation(price.multiply(BigDecimal.valueOf(1 + rate)).setScale(2, RoundingMode.HALF_UP), ChangeDirection.INCREASE, confidence, reasoning); }
}