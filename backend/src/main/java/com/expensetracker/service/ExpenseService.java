package com.expensetracker.service;

import com.expensetracker.google.GoogleSheetsClient;
import com.expensetracker.model.ExpenseRequest;
import com.expensetracker.model.OptionsResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Service
public class ExpenseService {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final GoogleSheetsClient googleSheetsClient;

    public ExpenseService(GoogleSheetsClient googleSheetsClient) {
        this.googleSheetsClient = googleSheetsClient;
    }

    public OptionsResponse getOptions() {
        return new OptionsResponse(
                googleSheetsClient.getOptions(googleSheetsClient.getBudgetSheet()),
                googleSheetsClient.getOptions(googleSheetsClient.getBankSheet()));
    }

    public void createExpense(ExpenseRequest request) {
        validate(request);
        LocalDateTime dateTime = LocalDateTime.parse(request.dateTime(), DATE_TIME_FORMAT);
        String sheetName = PeriodSheetName.forDate(dateTime.toLocalDate());
        googleSheetsClient.appendExpense(
                sheetName,
                request.dateTime(),
                request.name(),
                request.budget(),
                request.bank(),
                request.amount(),
                request.description());
    }

    private void validate(ExpenseRequest request) {
        if (request.dateTime() == null || request.dateTime().isBlank()) {
            throw new ValidationException("DateTime is required");
        }
        try {
            LocalDateTime.parse(request.dateTime(), DATE_TIME_FORMAT);
        } catch (DateTimeParseException e) {
            throw new ValidationException("DateTime must be in yyyy-MM-dd HH:mm format");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new ValidationException("Name is required");
        }
        if (request.name().length() > 255) {
            throw new ValidationException("Name must be at most 255 characters");
        }
        if (request.budget() == null || request.budget().isBlank()) {
            throw new ValidationException("Budget is required");
        }
        if (request.bank() == null || request.bank().isBlank()) {
            throw new ValidationException("Bank is required");
        }
        if (request.amount() == null) {
            throw new ValidationException("Amount is required");
        }
        if (request.amount() <= 0) {
            throw new ValidationException("Amount must be greater than 0");
        }
        if (request.description() != null && request.description().length() > 255) {
            throw new ValidationException("Description must be at most 255 characters");
        }
    }
}
