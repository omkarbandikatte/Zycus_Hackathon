package com.stockpulse.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product {
    @Id private String id;
    private String sku;
    private String name;
    @Enumerated(EnumType.STRING) private Category category;
    private BigDecimal currentPrice;
    private int stockLevel;
    private int reorderThreshold;
    private int demandVelocity;
    @Enumerated(EnumType.STRING) private ProductStatus status;
    private BigDecimal costPrice;
    private BigDecimal marginFloor;
    private String supplierId;

    protected Product() { }

    public Product(String id, String sku, String name, Category category, BigDecimal currentPrice, int stockLevel, int reorderThreshold, int demandVelocity) {
        this.id = id; this.sku = sku; this.name = name; this.category = category; this.currentPrice = currentPrice;
        this.stockLevel = stockLevel; this.reorderThreshold = reorderThreshold; this.demandVelocity = demandVelocity;
        refreshStatus();
    }

    public void changeStock(int amount) { if (stockLevel + amount < 0) throw new IllegalArgumentException("Stock cannot be negative"); stockLevel += amount; refreshStatus(); }
    public void recordSale() { changeStock(-1); demandVelocity++; }
    public void applyPrice(BigDecimal price) { if (price == null || price.signum() <= 0) throw new IllegalArgumentException("Price must be positive"); currentPrice = price; status = stockLevel == 0 ? ProductStatus.OUT_OF_STOCK : ProductStatus.ACTIVE; }
    public void markPriceReviewPending() { if (status != ProductStatus.OUT_OF_STOCK) status = ProductStatus.PRICE_REVIEW_PENDING; }
    private void refreshStatus() { status = stockLevel == 0 ? ProductStatus.OUT_OF_STOCK : status == ProductStatus.PRICE_REVIEW_PENDING ? status : ProductStatus.ACTIVE; }
    public String getId() { return id; } public String getSku() { return sku; } public String getName() { return name; } public Category getCategory() { return category; }
    public BigDecimal getCurrentPrice() { return currentPrice; } public int getStockLevel() { return stockLevel; } public int getReorderThreshold() { return reorderThreshold; }
    public int getDemandVelocity() { return demandVelocity; } public ProductStatus getStatus() { return status; } public BigDecimal getCostPrice() { return costPrice; }
    public BigDecimal getMarginFloor() { return marginFloor; } public String getSupplierId() { return supplierId; }
}