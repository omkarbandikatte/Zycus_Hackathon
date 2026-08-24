package com.stockpulse.advisor;
import com.stockpulse.domain.Product;
import com.stockpulse.domain.TriggerReason;
public record CommerceContext(Product product, double categoryAverageVelocity, TriggerReason triggerReason) { }