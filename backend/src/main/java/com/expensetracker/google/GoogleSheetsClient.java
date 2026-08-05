package com.expensetracker.google;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GoogleSheetsClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleSheetsClient.class);
    private static final String SHEET_NAME = "Expenses";

    private final Sheets sheets;
    private final String spreadsheetId;

    public GoogleSheetsClient(String credentialsPath, String spreadsheetId) {
        this.spreadsheetId = spreadsheetId;
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
            Spreadsheet spreadsheet = sheets.spreadsheets().get(spreadsheetId).execute();
            List<String> titles = new ArrayList<>();
            if (spreadsheet.getSheets() != null) {
                for (com.google.api.services.sheets.v4.model.Sheet sheet : spreadsheet.getSheets()) {
                    titles.add(sheet.getProperties().getTitle());
                    if (SHEET_NAME.equals(sheet.getProperties().getTitle())) {
                        return;
                    }
                }
            }
            throw new IllegalStateException(
                    "Sheet " + SHEET_NAME + " not found in spreadsheet, available sheets: " + titles);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to access spreadsheet", e);
        }
    }

    public void appendExpense(String date, String description, long amount) {
        ValueRange body = new ValueRange()
                .setValues(List.of(List.of(date, description, amount)));
        try {
            sheets.spreadsheets().values()
                    .append(spreadsheetId, SHEET_NAME + "!A:C", body)
                    .setValueInputOption("RAW")
                    .setInsertDataOption("INSERT_ROWS")
                    .execute();
        } catch (IOException e) {
            LOGGER.error("error from google sheets api", e);
            throw new IllegalStateException("Failed to append expense to google sheets", e);
        }
    }
}
