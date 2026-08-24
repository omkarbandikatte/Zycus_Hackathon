package com.stockpulse.advisor;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component
public class StrategyRegistry {
    private final Map<String, PricingStrategy> pricing; private final Map<String, ReorderStrategy> reorder; private volatile String active;
    public StrategyRegistry(Map<String, PricingStrategy> pricing, Map<String, ReorderStrategy> reorder, @Value("${stockpulse.strategy:rules}") String active) { this.pricing = pricing; this.reorder = reorder; this.active = active; }
    public PricingStrategy pricing() { return pricing.getOrDefault(active + "Pricing", pricing.get("rulesPricing")); }
    public ReorderStrategy reorder() { return reorder.getOrDefault(active + "Reorder", reorder.get("rulesReorder")); }
    public String active() { return active; }
    public synchronized void switchTo(String strategy) {
        var normalized = strategy == null ? "" : strategy.trim().toLowerCase();
        if (!pricing.containsKey(normalized + "Pricing") || !reorder.containsKey(normalized + "Reorder")) throw new IllegalArgumentException("Unknown strategy: " + strategy + ". Available strategies require pricing and reorder implementations.");
        active = normalized;
    }
}