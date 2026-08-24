package com.stockpulse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpulse.domain.Product;
import com.stockpulse.domain.ProductRepository;
import com.stockpulse.domain.ReorderSuggestion;
import com.stockpulse.domain.ReorderSuggestionRepository;
import com.stockpulse.domain.SuggestionStatus;
import com.stockpulse.domain.PricingSuggestion;
import com.stockpulse.domain.PricingSuggestionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "stockpulse.strategy=rules")
@AutoConfigureMockMvc
class CommerceWalkthroughTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private ProductRepository products;
    @Autowired private PricingSuggestionRepository pricing;
    @Autowired private ReorderSuggestionRepository reorders;

    @Test
    void orderCreatesBothSuggestionsAndApprovalUpdatesProduct() throws Exception {
        Product before = products.findById("PRD-003").orElseThrow();
        int expectedStockAfterOrder = before.getStockLevel() - 1;

        mvc.perform(post("/products/PRD-003/orders")).andExpect(status().isOk());
        awaitPendingSuggestions();

        List<PricingSuggestion> pricingSuggestions = pricing.findByStatus(SuggestionStatus.PENDING);
        List<ReorderSuggestion> reorderSuggestions = reorders.findByStatus(SuggestionStatus.PENDING);
        PricingSuggestion price = pricingSuggestions.stream().filter(item -> item.getProduct().getId().equals("PRD-003")).findFirst().orElseThrow();
        ReorderSuggestion reorder = reorderSuggestions.stream().filter(item -> item.getProduct().getId().equals("PRD-003")).findFirst().orElseThrow();
        assertThat(price.getTriggerReason().name()).isEqualTo("INVENTORY_LOW");
        assertThat(reorder.getTriggerReason().name()).isEqualTo("INVENTORY_LOW");
        assertThat(reorder.getCurrentStock()).isEqualTo(expectedStockAfterOrder);

        mvc.perform(patch("/pricing-suggestions/{id}", price.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"accept\":true}"))
            .andExpect(status().isOk());
        mvc.perform(patch("/reorder-suggestions/{id}", reorder.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"accept\":true}"))
            .andExpect(status().isOk());

        Product after = products.findById("PRD-003").orElseThrow();
        assertThat(after.getCurrentPrice()).isEqualByComparingTo(price.getRecommendedPrice());
        assertThat(after.getStockLevel()).isEqualTo(expectedStockAfterOrder + reorder.getRecommendedQuantity());
        assertThat(after.getStatus().name()).isEqualTo("ACTIVE");
    }

    @Test
    void strategyCanSwitchThroughRuntimeApi() throws Exception {
        String switched = mvc.perform(patch("/settings/strategy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"strategy\":\"AI\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode response = mapper.readTree(switched);
        assertThat(response.get("strategy").asText()).isEqualTo("ai");

        mvc.perform(patch("/settings/strategy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"strategy\":\"rules\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void highVelocityOrderCreatesDemandSpikeSuggestions() throws Exception {
        String created = mvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU-SPIKE-001\",\"name\":\"Viral Launch Item\",\"category\":\"APPAREL\",\"currentPrice\":19.99,\"stockLevel\":100,\"reorderThreshold\":10,\"demandVelocity\":30}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String productId = mapper.readTree(created).get("id").asText();

        mvc.perform(post("/products/{id}/orders", productId)).andExpect(status().isOk());
        awaitPendingSuggestionsFor(productId);

        assertThat(pricing.findByStatus(SuggestionStatus.PENDING).stream().filter(item -> item.getProduct().getId().equals(productId)).map(item -> item.getTriggerReason().name()).toList()).contains("DEMAND_SPIKE");
        assertThat(reorders.findByStatus(SuggestionStatus.PENDING).stream().filter(item -> item.getProduct().getId().equals(productId)).map(item -> item.getTriggerReason().name()).toList()).contains("DEMAND_SPIKE");
    }

    @Test
    void pricingStreamSendsReasoningAndSuggestionEvents() throws Exception {
        MvcResult started = mvc.perform(post("/products/PRD-001/suggest-pricing/stream"))
            .andExpect(request().asyncStarted())
            .andReturn();
        String stream = mvc.perform(asyncDispatch(started)).andReturn().getResponse().getContentAsString();
        assertThat(stream).contains("event:token", "event:suggestion", "data:");
    }

    @Test
    void rejectingPricingSuggestionRestoresActiveProductStatus() throws Exception {
        String created = mvc.perform(post("/products/PRD-004/suggest-pricing"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String suggestionId = mapper.readTree(created).get("id").asText();

        mvc.perform(patch("/pricing-suggestions/{id}", suggestionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accept\":false}"))
            .andExpect(status().isOk());

        assertThat(products.findById("PRD-004").orElseThrow().getStatus().name()).isEqualTo("ACTIVE");
    }

    @Test
    void repeatedManualSuggestionRequestsReusePendingSuggestion() throws Exception {
        String first = mvc.perform(post("/products/PRD-002/suggest-reorder"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post("/products/PRD-002/suggest-reorder"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        assertThat(mapper.readTree(second).get("id").asText()).isEqualTo(mapper.readTree(first).get("id").asText());
    }

    private void awaitPendingSuggestions() throws InterruptedException {
        awaitPendingSuggestionsFor("PRD-003");
    }

    private void awaitPendingSuggestionsFor(String productId) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            boolean pricingReady = pricing.findByStatus(SuggestionStatus.PENDING).stream().anyMatch(item -> item.getProduct().getId().equals(productId));
            boolean reorderReady = reorders.findByStatus(SuggestionStatus.PENDING).stream().anyMatch(item -> item.getProduct().getId().equals(productId));
            if (pricingReady && reorderReady) return;
            Thread.sleep(50);
        }
    }
}
