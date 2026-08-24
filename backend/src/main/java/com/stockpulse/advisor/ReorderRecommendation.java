package com.stockpulse.advisor;
public record ReorderRecommendation(int recommendedQuantity, int suggestedLeadTimeDays, double confidence, String reasoning) { }