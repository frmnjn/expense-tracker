package com.expensetracker.google;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleSheetsClientIntegrationTest {

    private static String credentialsPath;
    private static String spreadsheetId;
    private static GoogleSheetsClient client;

    @BeforeAll
    static void setup() {
        credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        spreadsheetId = System.getenv("GOOGLE_TEST_SHEET_ID");
        Assumptions.assumeTrue(credentialsPath != null && !credentialsPath.isBlank()
                && spreadsheetId != null && !spreadsheetId.isBlank(),
                "google test sheet not configured, skipping integration test");
        client = new GoogleSheetsClient(credentialsPath, spreadsheetId, "Budget", "TopUp");
    }

    @Test
    void ping_shouldAccessSpreadsheet() {
        assertDoesNotThrow(client::ping);
    }

    @Test
    void appendExpense_shouldAppendRow() {
        assertDoesNotThrow(() -> client.appendExpense(
                "2026-JUL-AUG", "2026-08-06 14:30", "Integration test",
                "Daily", 1, null));
    }

    @Test
    void createPeriodSheet_shouldCreateSheetWithHeaders() {
        assertDoesNotThrow(() -> client.appendExpense(
                "2026-INTEGRATION-TEST", "2026-08-06 14:30", "Integration test",
                "Daily", 1, null));
        assertTrue(client.sheetExists("2026-INTEGRATION-TEST"));
    }

    @Test
    void getOptions_shouldReturnLists() {
        Assumptions.assumeTrue(client.sheetExists("Budget"),
                "budget tab not configured, skipping options test");
        assertDoesNotThrow(() -> client.getOptions(client.getBudgetSheet()));
    }

    @Test
    void reorderSheets_shouldPutNewestPeriodFirstAndBudgetLast() {
        Assumptions.assumeTrue(client.sheetExists("Budget"),
                "budget tab not configured, skipping reorder test");
        assertDoesNotThrow(() -> {
            client.appendExpense(
                    "2025-JUN-JUL", "2025-06-25 14:30", "Reorder test",
                    "Daily", 1, null);
            client.appendExpense(
                    "2026-JUL-AUG", "2026-08-06 14:30", "Reorder test",
                    "Daily", 1, null);
            client.reorderSheets();
        });
        List<String> titles = client.getSheetTitlesInOrder();
        int newestPeriodIndex = titles.indexOf("2026-JUL-AUG");
        int oldestPeriodIndex = titles.indexOf("2025-JUN-JUL");
        assertTrue(newestPeriodIndex != -1 && oldestPeriodIndex != -1);
        assertTrue(newestPeriodIndex < oldestPeriodIndex);
        assertEquals("Budget", titles.get(titles.size() - 1));
    }
}
