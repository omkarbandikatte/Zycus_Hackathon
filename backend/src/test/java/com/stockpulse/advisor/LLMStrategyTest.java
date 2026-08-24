package com.stockpulse.advisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockpulse.domain.Category;
import com.stockpulse.domain.Product;
import com.stockpulse.domain.TriggerReason;
import java.math.BigDecimal;
import java.util.List;
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

    @Test
    void simulatedAiResponsesAreValidatedAcrossAllCommerceTriggers() {
        var pricing = new LLMCommerceStrategy(gateway, new RuleBasedPricingStrategy());
        var reorder = new LLMReorderStrategy(gateway, new RuleBasedReorderStrategy());
        var scenarios = List.of(
            new Scenario(TriggerReason.INVENTORY_LOW, "22.00", "INCREASE", 0.91, 25, 7),
            new Scenario(TriggerReason.DEMAND_SPIKE, "21.00", "INCREASE", 0.84, 30, 5),
            new Scenario(TriggerReason.MANUAL, "20.00", "HOLD", 0.73, 25, 7));

        for (var scenario : scenarios) {
            when(gateway.callLLM(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("{\"recommendedPrice\":\"" + scenario.price + "\",\"direction\":\"" + scenario.direction
                    + "\",\"confidence\":" + scenario.confidence + ",\"reasoning\":\"simulated decision\"}")
                .thenReturn("{\"recommendedQuantity\":" + scenario.quantity + ",\"suggestedLeadTimeDays\":"
                    + scenario.leadTime + ",\"confidence\":0.86,\"reasoning\":\"simulated replenishment\"}");

            var context = new CommerceContext(product, 10, scenario.trigger);
            var priceResult = pricing.recommend(context);
            var reorderResult = reorder.recommend(context);

            assertThat(priceResult.recommendedPrice()).isEqualByComparingTo(scenario.price);
            assertThat(priceResult.direction().name()).isEqualTo(scenario.direction);
            assertThat(priceResult.confidence()).isBetween(0.0, 1.0);
            assertThat(priceResult.reasoning()).isNotBlank();
            assertThat(reorderResult.recommendedQuantity()).isEqualTo(scenario.quantity);
            assertThat(reorderResult.suggestedLeadTimeDays()).isBetween(1, 365);
            assertThat(reorderResult.confidence()).isBetween(0.0, 1.0);
            assertThat(reorderResult.reasoning()).isNotBlank();
        }
    }

    @Test
    void simulatedStreamingAiResponseValidatesReasoningAndJson() throws Exception {
        var streamed = "Demand is elevated, so a modest price change protects availability. ===JSON==="
            + "{\"recommendedPrice\":\"21.00\",\"direction\":\"INCREASE\",\"confidence\":0.84}";
        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<String> onToken = invocation.getArgument(1);
            onToken.accept(streamed);
            return streamed;
        }).when(gateway).streamLLM(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        var pricing = new LLMCommerceStrategy(gateway, new RuleBasedPricingStrategy());
        var tokens = new StringBuilder();

        var result = pricing.streamRecommend(new CommerceContext(product, 10, TriggerReason.DEMAND_SPIKE), tokens::append);

        assertThat(result.recommendedPrice()).isEqualByComparingTo("21.00");
        assertThat(result.direction().name()).isEqualTo("INCREASE");
        assertThat(result.confidence()).isBetween(0.0, 1.0);
        assertThat(result.reasoning()).contains("Demand is elevated");
        assertThat(tokens).contains("Demand is elevated");
    }

    private record Scenario(TriggerReason trigger, String price, String direction, double confidence,
                            int quantity, int leadTime) { }

    private String capturePrompt() {
        var prompt = Argumen