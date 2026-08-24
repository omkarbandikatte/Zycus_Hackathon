package com.stockpulse.domain;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ReorderSuggestionRepository extends JpaRepository<ReorderSuggestion, String> { List<ReorderSuggestion> findByStatus(SuggestionStatus status); boolean existsByProductIdAndTriggerReasonAndStatus(String productId, TriggerReason reason, SuggestionStatus status); }