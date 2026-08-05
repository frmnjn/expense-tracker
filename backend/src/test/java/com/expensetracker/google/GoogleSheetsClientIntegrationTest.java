package com.expensetracker.google;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class GoogleSheetsClientIntegrationTest {

    private static String credentialsPath;
    private static String spreadsheetId;

    @BeforeAll
    static void setup() {
        credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        spreadsheetId = System.getenv("GOOGLE_SHEET_ID");
        Assumptions.assumeTrue(credentialsPath != null && !credentialsPath.isBlank()
                && spreadsheetId != null && !spreadsheetId.isBlank(),
                "google credentials not configured, skipping integration test");
    }

    @Test
    void ping_shouldAccessSpreadsheet() {
        GoogleSheetsClient client = new GoogleSheetsClient(credentialsPath, spreadsheetId);
        assertDoesNotThrow(client::ping);
    }

    @Test
    void appendExpense_shouldAppendRow() {
        GoogleSheetsClient client = new GoogleSheetsClient(credentialsPath, spreadsheetId);
        assertDoesNotThrow(() -> client.appendExpense("2026-08-06", "Integration test", 1));
    }
}
