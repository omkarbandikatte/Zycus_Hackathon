package com.stockpulse.advisor;
import org.springframework.stereotype.Component;
@Component("rulesReorder")
public class RuleBasedReorderStrategy implements ReorderStrategy {
    public ReorderRecommendation recommend(CommerceContext context) { var product = context.product(); return new ReorderRecommendation(Math.max(1, product.getReorderThreshold() * 3 - product.getStockLevel()), 7, 0.9, "Replenish to approximately three threshold cycles so the current signal has room to normalize."); }
}