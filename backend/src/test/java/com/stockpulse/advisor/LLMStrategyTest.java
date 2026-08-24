package com.stockpulse.advisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockpulse.domain.Category;
import com.stockpulse.domain.Product;
import com.stockpulse.domain.TriggerReason;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LLMStrategyTest {
    private final LLMGateway gateway = org.mockito.Mockito.mock(LLMGateway.class);
    private final Product product = new Product("TEST-001", "SKU-TEST-001", "Test Product", Category.APPAREL, new BigDecimal("20.00"), 5, 10, 30);

    @Test
    void promptsExplainDifferentMerchandisingDecisionsForEachTrigger() {
        var pricing = new LLMCommerceStrategy(gateway, new RuleBasedPricingStrategy());
        var reorder = new LLMReorderStrategy(gateway, new RuleBasedReorderStrategy());
        when(gateway.callLLM(anyString())).thenReturn("{\"recommendedPrice\":\"22.00\",\"direction\":\"INCREASE\",\"confidence\":0.8,\"reasoning\":\"protect availability\"}");

        pricing.recommend(new CommerceContext(product, 5, TriggerReason.INVENTORY_LOW));
        var lowPrompt = capturePrompt();
        pricing.recommend(new CommerceContext(product, 5, TriggerReason.DEMAND_SPIKE));
        var spikePrompt = capturePrompt();
        assertThat(lowPrompt).contains("below threshold", "scarcity protection");
        assertThat(spikePrompt).contains("spiked", "customer trust");

        reorder.recommend(new CommerceContext(product, 5, TriggerReason.INVENTORY_LOW));
        var reorderLowPrompt = capturePrompt();
        reorder.recommend(new CommerceContext(product, 5, TriggerReason.DEMAND_SPIKE));
        var reorderSpikePrompt = capturePrompt();
        assertThat(reorderLowPrompt).contains("inbound shipment", "overbuying");
        assertThat(reorderSpikePrompt).contains("accelerating", "elevated run rate");
    }

    @Test
    void unsafeAiResponsesUseDeterministicFallbacks() {
        when(gateway.callLLM(anyString())).thenReturn("{\"recommendedPrice\":\"9999\",\"direction\":\"INCREASE\",\"confidence\":2,\"reasoning\":\"unsafe\"}");
        var pricing = new LLMCommerceStrategy(gateway, new RuleBasedPricingStrategy());
        var result = pricing.recommend(new CommerceContext(product, 5, TriggerReason.INVENTORY_LOW));
        assertThat(result.recommendedPrice()).isEqualByComparingTo("22.00");
        assertThat(result.confidence()).isEqualTo(0.88);

        when(gateway.callLLM(anyString())).thenReturn("{\"recommendedQuantity\":0,\"suggestedLeadTimeDays\":0,\"confidence\":-1,\"reasoning\":\"unsafe\"}");
        var reorder = new LLMReorderStrategy(gateway, new RuleBasedReorderStrategy());
        var reorderResult = reorder.recommend(new CommerceContext(product, 5, TriggerReason.INVENTORY_LOW));
        assertThat(reorderResult.recommendedQuantity()).isEqualTo(25);
        assertThat(reorderResult.confidence()).isEqualTo(0.9);
    }

    private String capturePrompt() {
        var prompt = ArgumentCaptor.forClass(String.class);
        verify(gateway, org.mockito.Mockito.atLeastOnce()).callLLM(prompt.capture());
        return prompt.getAllValues().get(prompt.getAllValues().size() - 1);
    }
}
