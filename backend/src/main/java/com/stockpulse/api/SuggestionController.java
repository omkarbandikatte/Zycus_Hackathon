package com.stockpulse.api;
import com.stockpulse.domain.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;
@RestController
public class SuggestionController {
    private final PricingSuggestionRepository pricing; private final ReorderSuggestionRepository reorder; private final com.stockpulse.advisor.CommerceAdvisorService advisor;
    public SuggestionController(PricingSuggestionRepository pricing, ReorderSuggestionRepository reorder, com.stockpulse.advisor.CommerceAdvisorService advisor) { this.pricing = pricing; this.reorder = reorder; this.advisor = advisor; }
    @GetMapping("/pricing-suggestions") public List<PricingSuggestion> pricing(@RequestParam(defaultValue = "PENDING") SuggestionStatus status) { return pricing.findByStatus(status); }
    @GetMapping("/reorder-suggestions") public List<ReorderSuggestion> reorder(@RequestParam(defaultValue = "PENDING") SuggestionStatus status) { return reorder.findByStatus(status); }
    @PatchMapping("/pricing-suggestions/{id}") public PricingSuggestion decidePricing(@PathVariable String id, @Valid @RequestBody ApiDtos.Decision decision) { return advisor.decidePricing(id, decision.accept()); }
    @PatchMapping("/reorder-suggestions/{id}") public ReorderSuggestion decideReorder(@PathVariable String id, @Valid @RequestBody ApiDtos.Decision decision) { return advisor.decideReorder(id, decision.accept()); }
}