package com.stockpulse.api;

import com.stockpulse.advisor.StrategyRegistry;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/settings/strategy")
public class StrategyController {
    private final StrategyRegistry registry;
    private final double demandSpikeMultiplier;

    public StrategyController(StrategyRegistry registry, @org.springframework.beans.factory.annotation.Value("${stockpulse.demand-spike-multiplier:3}") double demandSpikeMultiplier) {
        this.registry = registry;
        this.demandSpikeMultiplier = demandSpikeMultiplier;
    }

    @GetMapping
    public Map<String, String> current() {
        return Map.of("strategy", registry.active());
    }

    @GetMapping("/signals")
    public Map<String, Double> signals() {
        return Map.of("demandSpikeMultiplier", demandSpikeMultiplier);
    }

    @PatchMapping
    public Map<String, String> change(@Valid @RequestBody ApiDtos.StrategyChange request) {
        registry.switchTo(request.strategy());
        return current();
    }
}