package com.expensetracker.config;

import com.expensetracker.google.GoogleSheetsClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AppConfig {

    @Value("${google.sheet-id}")
    private String sheetId;

    @Value("${google.application-credentials}")
    private String applicationCredentials;

    @Value("${google.budget-sheet}")
    private String budgetSheet;

    @Bean
    public GoogleSheetsClient googleSheetsClient() {
        return new GoogleSheetsClient(applicationCredentials, sheetId, budgetSheet);
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("*")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("Content-Type");
            }
        };
    }
}
