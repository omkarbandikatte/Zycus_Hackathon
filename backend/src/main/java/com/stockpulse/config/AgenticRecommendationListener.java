package com.stockpulse.config;
import com.stockpulse.advisor.CommerceAdvisorService;
import com.stockpulse.api.CommerceSignalEvent;
import com.stockpulse.domain.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
@Component
public class AgenticRecommendationListener {
    private final CommerceAdvisorService advisor; private final PricingSuggestionRepository pricing; private final ReorderSuggestionRepository reorder;
    public AgenticRecommendationListener(CommerceAdvisorService advisor, PricingSuggestionRepository pricing, ReorderSuggestionRepository reorder) { this.advisor = advisor; this.pricing = pricing; this.reorder = reorder; }
    @Async @Transactional(propagation = Propagation.REQUIRES_NEW) @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public synchronized void onSignal(CommerceSignalEvent event) {
        if (!pricing.existsByProductIdAndTriggerReasonAndStatus(event.productId(), event.reason(), SuggestionStatus.PENDING)) advisor.suggestPricing(event.productId(), event.reason());
        if (!reorder.existsByProductIdAndTriggerReasonAndStatus(event.productId(), event.reason(), SuggestionStatus.PENDING)) advisor.suggestReorder(event.productId(), event.reason());
    }
}