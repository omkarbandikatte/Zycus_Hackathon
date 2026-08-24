package com.stockpulse.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
@Configuration
public class CorsConfig {
    @Bean WebMvcConfigurer corsConfigurer() { return new WebMvcConfigurer() { public void addCorsMappings(CorsRegistry registry) { registry.addMapping("/**").allowedOrigins("http://localhost:5173", "http://localhost:4200").allowedMethods("GET", "POST", "PATCH", "OPTIONS"); } }; }
}