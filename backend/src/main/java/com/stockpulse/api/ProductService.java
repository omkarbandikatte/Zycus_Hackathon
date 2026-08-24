package com.stockpulse.api;
import com.stockpulse.advisor.CommerceAdvisorService;
import com.stockpulse.domain.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
@Service
public class ProductService {
    private final ProductRepository products; private final CommerceAdvisorService advisor; private final ApplicationEventPublisher events; private final double demandSpikeMultiplier;
    public ProductService(ProductRepository products, CommerceAdvisorService advisor, ApplicationEventPublisher events, @Value("${stockpulse.demand-spike-multiplier:3}") double demandSpikeMultiplier) { this.products = products; this.advisor = advisor; this.events = events; this.demandSpikeMultiplier = demandSpikeMultiplier; }
    @Transactional public Product create(ApiDtos.CreateProduct request) { return products.save(new Product(java.util.UUID.randomUUID().toString(), request.sku(), request.name(), request.category(), request.currentPrice(), request.stockLevel(), request.reorderThreshold(), request.demandVelocity())); }
    @Transactional public Product changeStock(String id, int amount) { var product = advisor.product(id); product.changeStock(amount); products.save(product); publishSignals(product); return product; }
    @Transactional public Product order(String id) { var product = advisor.product(id); product.recordSale(); products.save(product); publishSignals(product); return product; }
    private void publishSignals(Product product) { if (product.getStockLevel() < product.getReorderThreshold()) events.publishEvent(new CommerceSignalEvent(product.getId(), TriggerReason.INVENTORY_LOW)); if (product.getDemandVelocity() > 0 && product.getDemandVelocity() > demandSpikeMultiplier * average(product.getCategory(), product.getId())) events.publishEvent(new CommerceSignalEvent(product.getId(), TriggerReason.DEMAND_SPIKE)); }
    private double average(Category category, String excludedProductId) { return products.findByCategory(category).stream().filter(item -> !item.getId().equals(excludedProductId)).mapToInt(Product::getDemandVelocity).average().orElse(0); }
}