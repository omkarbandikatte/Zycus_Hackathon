package com.stockpulse.api;
import com.stockpulse.domain.TriggerReason;
public record CommerceSignalEvent(String productId, TriggerReason reason) { }