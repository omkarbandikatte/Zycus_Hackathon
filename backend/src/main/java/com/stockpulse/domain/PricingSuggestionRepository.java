package com.stockpulse.domain;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PricingSuggestionRepository extends JpaRepository<PricingSuggestion, String> { List<PricingSuggestion> findByStatus(SuggestionStatus status); boolean existsByProductIdAndTriggerReasonAndStatus(String productId, TriggerReason reason, SuggestionStatus status); }