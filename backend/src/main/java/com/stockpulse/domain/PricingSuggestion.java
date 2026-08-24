package com.stockpulse.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity @Table(name = "pricing_suggestions")
public class PricingSuggestion {
    @Id private String id = UUID.randomUUID().toString();
    @ManyToOne(optional = false) private Product product;
    private BigDecimal currentPrice; private BigDecimal recommendedPrice;
    @Enumerated(EnumType.STRING) private ChangeDirection direction;
    private double confidence; @Column(length = 2000) private String reasoning;
    @Enumerated(EnumType.STRING) private SuggestionStatus status = SuggestionStatus.PENDING;
    @Enumerated(EnumType.STRING) private TriggerReason triggerReason;
    protected PricingSuggestion() { }
    public PricingSuggestion(Product product, BigDecimal currentPrice, BigDecimal recommendedPrice, ChangeDirection direction, double confidence, String reasoning, TriggerReason triggerReason) {
        this.product = product; this.currentPrice = currentPrice; this.recommendedPrice = recommendedPrice; this.direction = direction; this.confidence = confidence; this.reasoning = reasoning; this.triggerReason = triggerReason;
    }
    public void accept() { requirePending(); status = SuggestionStatus.ACCEPTED; product.applyPrice(recommendedPrice); }
    public void reject() { requirePending(); status = SuggestionStatus.REJECTED; }
    private void requirePending() { if (status != SuggestionStatus.PENDING) throw new IllegalStateException("Suggestion is already decided"); }
    public String getId() { return id; } public Product getProduct() { return product; } public BigDecimal getCurrentPrice() { return currentPrice; } public BigDecimal getRecommendedPrice() { return recommendedPrice; }
    public ChangeDirection getDirection() { return direction; } public double getConfidence() { return confidence; } public String getReasoning() { return reasoning; } public SuggestionStatus getStatus() { return status; } public TriggerReason getTriggerReason() { return triggerReason; }
}