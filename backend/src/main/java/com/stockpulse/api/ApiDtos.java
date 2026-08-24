package com.stockpulse.api;
import com.stockpulse.domain.Category;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public final class ApiDtos {
    private ApiDtos() { }
    public record CreateProduct(@NotBlank String sku, @NotBlank String name, @NotNull Category category, @NotNull @Positive BigDecimal currentPrice, @PositiveOrZero int stockLevel, @Positive int reorderThreshold, @PositiveOrZero int demandVelocity) { }
    public record StockChange(@NotNull Integer amount) { }
    public record Decision(@NotNull Boolean accept) { }
    public record StrategyChange(@NotBlank String strategy) { }
}