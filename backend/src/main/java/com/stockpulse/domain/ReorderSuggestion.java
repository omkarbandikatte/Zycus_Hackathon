package com.stockpulse.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "reorder_suggestions")
public class ReorderSuggestion {
    @Id private String id = UUID.randomUUID().toString();
    @ManyToOne(optional = false) private Product product;
    private int currentStock; private int recommendedQuantity; private int suggestedLeadTimeDays;
    private double confidence; @Column(length = 2000) private String reasoning;
    @Enumerated(EnumType.STRING) private SuggestionStatus status = SuggestionStatus.PENDING;
    @Enumerated(EnumType.STRING) private TriggerReason triggerReason;
    protected ReorderSuggestion() { }
    public ReorderSuggestion(Product product, int currentStock, int recommendedQuantity, int leadTimeDays, double confidence, String reasoning, TriggerReason triggerReason) {
        this.product = product; this.currentStock = currentStock; this.recommendedQuantity = recommendedQuantity; this.suggestedLeadTimeDays = leadTimeDays; this.confidence = confidence; this.reasoning = reasoning; this.triggerReason = triggerReason;
    }
    public void accept() { if (status != SuggestionStatus.PENDING) throw new IllegalStateException("Suggestion is already decided"); status = SuggestionStatus.ACCEPTED; product.changeStock(recommendedQuantity); }
    public void reject() { if (status != SuggestionStatus.PENDING) throw new IllegalStateException("Suggestion is already decided"); status = SuggestionStatus.REJECTED; }
    public String getId() { return id; } public Product getProduct() { return product; } public int getCurrentStock() { return currentStock; } public int getRecommendedQuantity() { return recommendedQuantity; }
    public int getSuggestedLeadTimeDays() { return suggestedLeadTimeDays; } public double getConfidence() { return confidence; } public String getReasoning() { return reasoning; } public SuggestionStatus getStatus() { return status; } public TriggerReason getTriggerReason() { return triggerReason; }
}