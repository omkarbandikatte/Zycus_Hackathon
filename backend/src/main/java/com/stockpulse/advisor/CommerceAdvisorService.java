package com.stockpulse.advisor;
import com.stockpulse.domain.*;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class CommerceAdvisorService {
    private final StrategyRegistry registry; private final ProductRepository products; private final PricingSuggestionRepository pricing; private final ReorderSuggestionRepository reorder;
    public CommerceAdvisorService(StrategyRegistry registry, ProductRepository products, PricingSuggestionRepository pricing, ReorderSuggestionRepository reorder) { this.registry = registry; this.products = products; this.pricing = pricing; this.reorder = reorder; }
    public PricingSuggestion suggestPricing(String productId, TriggerReason reason) { var product = product(productId); var context = new CommerceContext(product, categoryAverage(product.getCategory()), reason); var result = registry.pricing().recommend(context); validate(result, product); product.markPriceReviewPending(); return pricing.save(new PricingSuggestion(product, product.getCurrentPrice(), result.recommendedPrice(), result.direction(), result.confidence(), result.reasoning(), reason)); }
    /** Streams the active strategy's live reasoning via onToken, then persists the resulting suggestion. Non-AI strategies emit their reasoning as a single token. */
    public void streamPricing(String productId, TriggerReason reason, Consumer<String> onToken, Consumer<PricingSuggestion> onComplete, Consumer<Throwable> onError) {
        try {
            var product = product(productId);
            var context = new CommerceContext(product, categoryAverage(product.getCategory()), reason);
            var strategy = registry.pricing();
            var result = strategy instanceof LLMCommerceStrategy ai ? ai.streamRecommend(context, onToken) : recommendAndEmit(strategy, context, onToken);
            validate(result, product);
            product.markPriceReviewPending();
            var suggestion = pricing.save(new PricingSuggestion(product, product.getCurrentPrice(), result.recommendedPrice(), result.direction(), result.confidence(), result.reasoning(), reason));
            onComplete.accept(suggestion);
        } catch (Exception exception) {
            onError.accept(exception);
        }
    }
    private PricingRecommendation recommendAndEmit(PricingStrategy strategy, CommerceContext context, Consumer<String> onToken) { var result = strategy.recommend(context); onToken.accept(result.reasoning()); return result; }
    public ReorderSuggestion suggestReorder(String productId, TriggerReason reason) { var product = product(productId); var result = registry.reorder().recommend(new CommerceContext(product, categoryAverage(product.getCategory()), reason)); if (result.recommendedQuantity() < 1) throw new IllegalArgumentException("Recommendation quantity must be positive"); return reorder.save(new ReorderSuggestion(product, product.getStockLevel(), result.recommendedQuantity(), result.suggestedLeadTimeDays(), result.confidence(), result.reasoning(), reason)); }
    private void validate(PricingRecommendation result, Product product) { if (result.recommendedPrice() == null || result.recommendedPrice().signum() <= 0 || result.recommendedPrice().compareTo(product.getCurrentPrice().multiply(java.math.BigDecimal.TEN)) > 0) throw new IllegalArgumentException("Recommendation price is outside safe bounds"); if (result.confidence() < 0 || result.confidence() > 1) throw new IllegalArgumentException("Confidence must be between 0 and 1"); }
    private double categoryAverage(Category category) { return products.findByCategory(category).stream().mapToInt(Product::getDemandVelocity).average().orElse(0); }
    public Product product(String id) { return products.findById(id).orElseThrow(() -> new java.util.NoSuchElementException("Product not found: " + id)); }
    @Transactional public PricingSuggestion decidePricing(String id, boolean accept) { var item = pricing.findById(id).orElseThrow(); if (accept) item.accept(); else item.reject(); return pricing.save(item); }
    @Transactional public ReorderSuggestion decideReorder(String id, boolean accept) { var item = reorder.findById(id).orElseThrow(); if (accept) item.accept(); else item.reject(); return reorder.save(item); }
}