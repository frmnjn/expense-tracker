package com.expensetracker.config;

import com.expensetracker.google.GoogleSheetsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class SheetOrderInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SheetOrderInitializer.class);

    private final GoogleSheetsClient googleSheetsClient;

    public SheetOrderInitializer(GoogleSheetsClient googleSheetsClient) {
        this.googleSheetsClient = googleSheetsClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            googleSheetsClient.reorderSheets();
            googleSheetsClient.formatBudgetBalanceColumn();
        } catch (Exception e) {
            LOGGER.error("failed to initialize sheets on startup", e);
        }
    }
}
