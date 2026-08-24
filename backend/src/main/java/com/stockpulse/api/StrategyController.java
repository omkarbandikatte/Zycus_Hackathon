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

    public StrategyController(StrategyRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public Map<String, String> current() {
        return Map.of("strategy", registry.active());
    }

    @PatchMapping
    public Map<String, String> change(@Valid @RequestBody ApiDtos.StrategyChange request) {
        registry.switchTo(request.strategy());
        return current();
    }
}