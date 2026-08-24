package com.stockpulse.api;
import com.stockpulse.domain.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/products")
public class ProductController {
    private final ProductRepository products; private final ProductService service; private final com.stockpulse.advisor.CommerceAdvisorService advisor;
    public ProductController(ProductRepository products, ProductService service, com.stockpulse.advisor.CommerceAdvisorService advisor) { this.products = products; this.service = service; this.advisor = advisor; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Product create(@Valid @RequestBody ApiDtos.CreateProduct request) { return service.create(request); }
    @GetMapping public List<Product> list(@RequestParam(required = false) ProductStatus status, @RequestParam(required = false) Category category) { if (status != null && category != null) return products.findByStatusAndCategory(status, category); if (status != null) return products.findByStatus(status); if (category != null) return products.findByCategory(category); return products.findAll(); }
    @PatchMapping("/{id}/stock") public Product stock(@PathVariable String id, @Valid @RequestBody ApiDtos.StockChange request) { return service.changeStock(id, request.amount()); }
    @PostMapping("/{id}/orders") public Product order(@PathVariable String id) { return service.order(id); }
    @PostMapping("/{id}/suggest-pricing") @ResponseStatus(HttpStatus.CREATED) public PricingSuggestion pricing(@PathVariable String id) { return advisor.suggestPricing(id, TriggerReason.MANUAL); }
    @PostMapping("/{id}/suggest-reorder") @ResponseStatus(HttpStatus.CREATED) public ReorderSuggestion reorder(@PathVariable String id) { return advisor.suggestReorder(id, TriggerReason.MANUAL); }
    @PostMapping(value = "/{id}/suggest-pricing/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter pricingStream(@PathVariable String id) {
        var emitter = new SseEmitter(60_000L);
        java.util.concurrent.CompletableFuture.runAsync(() -> advisor.streamPricing(id, TriggerReason.MANUAL,
            token -> sendEvent(emitter, "token", token),
            suggestion -> { sendEvent(emitter, "suggestion", suggestion); emitter.complete(); },
            emitter::completeWithError));
        return emitter;
    }
    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try { emitter.send(SseEmitter.event().name(name).data(data)); } catch (java.io.IOException ignored) { }
    }
}