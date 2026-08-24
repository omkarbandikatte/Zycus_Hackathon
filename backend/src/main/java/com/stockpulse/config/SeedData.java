package com.stockpulse.config;
import com.stockpulse.domain.*;
import java.math.BigDecimal;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class SeedData {
    @Bean CommandLineRunner seed(ProductRepository products) { return args -> { if (products.count() > 0) return; products.saveAll(java.util.List.of(
        product("PRD-001", "SKU-ELEC-001", "Wireless Earbuds Pro", Category.ELECTRONICS, "79.99", 45, 20, 3), product("PRD-002", "SKU-ELEC-002", "USB-C Hub 7-Port", Category.ELECTRONICS, "34.99", 120, 30, 1), product("PRD-003", "SKU-APP-001", "Organic Cotton T-Shirt", Category.APPAREL, "24.99", 8, 15, 12), product("PRD-004", "SKU-APP-002", "Running Shorts - Navy", Category.APPAREL, "39.99", 55, 20, 2), product("PRD-005", "SKU-HOME-001", "Ceramic Pour-Over Set", Category.HOME, "49.99", 22, 10, 4), product("PRD-006", "SKU-HOME-002", "LED Desk Lamp - Dimmable", Category.HOME, "59.99", 0, 15, 0), product("PRD-007", "SKU-ELEC-003", "Portable Charger 20K", Category.ELECTRONICS, "44.99", 18, 25, 8), product("PRD-008", "SKU-APP-003", "Hoodie - Heather Grey", Category.APPAREL, "54.99", 11, 12, 15)
    )); }; }
    private Product product(String id, String sku, String name, Category category, String price, int stock, int threshold, int velocity) { return new Product(id, sku, name, category, new BigDecimal(price), stock, threshold, velocity); }
}