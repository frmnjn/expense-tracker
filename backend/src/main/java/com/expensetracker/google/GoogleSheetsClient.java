package com.expensetracker.google;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.CellFormat;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.NumberFormat;
import com.google.api.services.sheets.v4.model.RepeatCellRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.UpdateSheetPropertiesRequest;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.expensetracker.service.PeriodSheetName;
import com.expensetracker.model.BudgetOption;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class GoogleSheetsClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleSheetsClient.class);
    private static final List<Object> HEADERS = List.of("Waktu", "Name", "Budget", "Nominal", "Description");

    private final Sheets sheets;
    private final String spreadsheetId;
    private final String budgetSheet;

    public GoogleSheetsClient(String credentialsPath, String spreadsheetId, String budgetSheet) {
        this.spreadsheetId = spreadsheetId;
        this.budgetSheet = budgetSheet;
        this.sheets = createSheetsService(credentialsPath);
    }

    private static Sheets createSheetsService(String credentialsPath) {
        try {
            NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
            GoogleCredentials credentials;
            try (FileInputStream in = new FileInputStream(credentialsPath)) {
                credentials = GoogleCredentials.fromStream(in)
                        .createScoped(Collections.singletonList(SheetsScopes.SPREADSHEETS));
            }
            HttpRequestInitializer initializer = new HttpCredentialsAdapter(credentials);
            return new Sheets.Builder(transport, GsonFactory.getDefaultInstance(), initializer)
                    .setApplicationName("Expense Tracker")
                    .build();
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Failed to create google sheets client", e);
        }
    }

    public void ping() {
        try {
            sheets.spreadsheets().get(spreadsheetId).execute();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to access spreadsheet", e);
        }
    }

    public void appendExpense(String sheetName, String dateTime, String name,
                              String budget, long amount, String description) {
        ensureSheetExists(sheetName);
        ValueRange body = new ValueRange()
                .setValues(List.of(List.of(dateTime, name, budget, amount,
                        description == null ? "" : description)));
        try {
            sheets.spreadsheets().values()
                    .append(spreadsheetId, sheetName + "!A:E", body)
                    .setValueInputOption("USER_ENTERED")
                    .setInsertDataOption("INSERT_ROWS")
                    .execute();
        } catch (IOException e) {
            LOGGER.error("error from google sheets api", e);
            throw new IllegalStateException("Failed to append expense to google sheets", e);
        }
    }

    public List<BudgetOption> getOptions(String sheetName) {
        try {
            ValueRange result = sheets.spreadsheets().values()
                    .get(spreadsheetId, sheetName + "!A:B")
                    .execute();
            List<BudgetOption> options = new ArrayList<>();
            if (result.getValues() == null) {
                return options;
            }
            for (int i = 1; i < result.getValues().size(); i++) {
                List<Object> row = result.getValues().get(i);
                if (row == null || row.isEmpty() || row.get(0) == null) {
                    continue;
                }
                String name = String.valueOf(row.get(0)).trim();
                if (name.isBlank()) {
                    continue;
                }
                options.add(new BudgetOption(name, parseBalance(row.size() > 1 ? row.get(1) : null)));
            }
            return options;
        } catch (IOException e) {
            LOGGER.error("error reading options from google sheets api", e);
            throw new IllegalStateException("Failed to read options from google sheets", e);
        }
    }

    public void decrementBudget(String budget, long amount) {
        try {
            ValueRange result = sheets.spreadsheets().values()
                    .get(spreadsheetId, budgetSheet + "!A:B")
                    .execute();
            List<List<Object>> values = result.getValues();
            if (values == null) {
                return;
            }
            int rowIndex = -1;
            for (int i = 0; i < values.size(); i++) {
                List<Object> row = values.get(i);
                if (row != null && !row.isEmpty() && budget.equals(String.valueOf(row.get(0)).trim())) {
                    rowIndex = i;
                    break;
                }
            }
            if (rowIndex < 0) {
                LOGGER.warn("budget not found for decrement: budget={}", budget);
                return;
            }
            long current = parseBalance(values.get(rowIndex).size() > 1
                    ? values.get(rowIndex).get(1) : null);
            long updated = current - amount;
            ValueRange body = new ValueRange().setValues(List.of(List.of(updated)));
            sheets.spreadsheets().values()
                    .update(spreadsheetId, budgetSheet + "!B" + (rowIndex + 1), body)
                    .setValueInputOption("RAW")
                    .execute();
        } catch (IOException e) {
            LOGGER.error("error decrementing budget in google sheets api", e);
            throw new IllegalStateException("Failed to decrement budget in google sheets", e);
        }
    }

    private static long parseBalance(Object value) {
        if (value == null) {
            return 0;
        }
        String raw = String.valueOf(value).trim();
        if (raw.isEmpty()) {
            return 0;
        }
        boolean negative = raw.startsWith("-") || raw.startsWith("(");
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            long parsed = Long.parseLong(digits);
            return negative ? -parsed : parsed;
        } catch (NumberFormatException e) {
            LOGGER.warn("failed to parse balance value={}", value);
            return 0;
        }
    }

    public String getBudgetSheet() {
        return budgetSheet;
    }

    public void reorderSheets() {
        try {
            List<Sheet> existingSheets = sheets.spreadsheets().get(spreadsheetId).execute().getSheets();
            if (existingSheets == null || existingSheets.isEmpty()) {
                return;
            }
            List<String> titles = new ArrayList<>();
            for (Sheet sheet : existingSheets) {
                titles.add(sheet.getProperties().getTitle());
            }

            List<String> periodSheets = new ArrayList<>();
            List<String> otherSheets = new ArrayList<>();
            for (String title : titles) {
                if (PeriodSheetName.parseStartDate(title).isPresent()) {
                    periodSheets.add(title);
                } else {
                    otherSheets.add(title);
                }
            }
            periodSheets.sort(Comparator
                    .comparing((String title) -> PeriodSheetName.parseStartDate(title).orElse(LocalDate.MIN))
                    .reversed());

            List<String> ordered = new ArrayList<>();
            ordered.addAll(periodSheets);
            for (String title : otherSheets) {
                if (!budgetSheet.equals(title)) {
                    ordered.add(title);
                }
            }
            if (titles.contains(budgetSheet)) {
                ordered.add(budgetSheet);
            }

            List<Request> requests = new ArrayList<>();
            for (int index = 0; index < ordered.size(); index++) {
                String title = ordered.get(index);
                int currentIndex = titles.indexOf(title);
                if (currentIndex != index) {
                    SheetProperties properties = new SheetProperties()
                            .setSheetId(findSheetId(existingSheets, title))
                            .setIndex(index);
                    UpdateSheetPropertiesRequest update = new UpdateSheetPropertiesRequest()
                            .setProperties(properties)
                            .setFields("index");
                    requests.add(new Request().setUpdateSheetProperties(update));
                }
            }
            if (!requests.isEmpty()) {
                BatchUpdateSpreadsheetRequest batch = new BatchUpdateSpreadsheetRequest().setRequests(requests);
                sheets.spreadsheets().batchUpdate(spreadsheetId, batch).execute();
            }
        } catch (IOException e) {
            LOGGER.error("failed to reorder sheets", e);
        }
    }

    public List<String> getSheetTitlesInOrder() {
        try {
            List<Sheet> existingSheets = sheets.spreadsheets().get(spreadsheetId).execute().getSheets();
            List<String> titles = new ArrayList<>();
            if (existingSheets != null) {
                for (Sheet sheet : existingSheets) {
                    titles.add(sheet.getProperties().getTitle());
                }
            }
            return titles;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to get sheet titles", e);
        }
    }

    private static Integer findSheetId(List<Sheet> sheets, String title) {
        for (Sheet sheet : sheets) {
            if (title.equals(sheet.getProperties().getTitle())) {
                return sheet.getProperties().getSheetId();
            }
        }
        return null;
    }

    private void ensureSheetExists(String sheetName) {
        if (sheetExists(sheetName)) {
            return;
        }
        createSheet(sheetName);
    }

    public boolean sheetExists(String sheetName) {
        try {
            Spreadsheet spreadsheet = sheets.spreadsheets().get(spreadsheetId).execute();
            if (spreadsheet.getSheets() != null) {
                for (com.google.api.services.sheets.v4.model.Sheet sheet : spreadsheet.getSheets()) {
                    if (sheetName.equals(sheet.getProperties().getTitle())) {
                        return true;
                    }
                }
            }
            return false;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to check sheet existence", e);
        }
    }

    private void createSheet(String sheetName) {
        AddSheetRequest addSheet = new AddSheetRequest()
                .setProperties(new SheetProperties().setTitle(sheetName));
        Request request = new Request().setAddSheet(addSheet);
        BatchUpdateSpreadsheetRequest batch = new BatchUpdateSpreadsheetRequest().setRequests(List.of(request));
        try {
            sheets.spreadsheets().batchUpdate(spreadsheetId, batch).execute();
            writeHeaders(sheetName);
            formatCurrencyColumn(sheetName, 3);
            reorderSheets();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create sheet " + sheetName, e);
        }
    }

    public void formatBudgetBalanceColumn() {
        formatCurrencyColumn(budgetSheet, 1);
    }

    private void formatCurrencyColumn(String sheetName, int columnIndex) {
        try {
            Spreadsheet spreadsheet = sheets.spreadsheets().get(spreadsheetId).execute();
            Integer sheetId = findSheetId(spreadsheet.getSheets(), sheetName);
            if (sheetId == null) {
                return;
            }
            CellFormat currencyFormat = new CellFormat()
                    .setNumberFormat(new NumberFormat()
                            .setType("CURRENCY")
                            .setPattern("Rp #,##0"));
            RepeatCellRequest repeat = new RepeatCellRequest()
                    .setCell(new CellData().setUserEnteredFormat(currencyFormat))
                    .setFields("userEnteredFormat.numberFormat")
                    .setRange(new GridRange()
                            .setSheetId(sheetId)
                            .setStartColumnIndex(columnIndex)
                            .setEndColumnIndex(columnIndex + 1));
            BatchUpdateSpreadsheetRequest batch = new BatchUpdateSpreadsheetRequest()
                    .setRequests(List.of(new Request().setRepeatCell(repeat)));
            sheets.spreadsheets().batchUpdate(spreadsheetId, batch).execute();
        } catch (IOException e) {
            LOGGER.error("failed to format currency column {} in sheet {}", columnIndex, sheetName, e);
        }
    }

    private void writeHeaders(String sheetName) {
        ValueRange body = new ValueRange().setValues(List.of(HEADERS));
        try {
            sheets.spreadsheets().values()
                    .update(spreadsheetId, sheetName + "!A1:F1", body)
                    .setValueInputOption("RAW")
                    .execute();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write headers to sheet " + sheetName, e);
        }
    }
}
