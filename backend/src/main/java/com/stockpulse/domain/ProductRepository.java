package com.stockpulse.domain;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ProductRepository extends JpaRepository<Product, String> { List<Product> findByStatusAndCategory(ProductStatus status, Category category); List<Product> findByStatus(ProductStatus status); List<Product> findByCategory(Category category); }