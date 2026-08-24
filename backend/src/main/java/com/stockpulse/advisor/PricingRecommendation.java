package com.stockpulse.advisor;
import com.stockpulse.domain.ChangeDirection;
import java.math.BigDecimal;
public record PricingRecommendation(BigDecimal recommendedPrice, ChangeDirection direction, double confidence, String reasoning) { }