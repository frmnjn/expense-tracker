package com.expensetracker.service;

import com.expensetracker.google.GoogleSheetsClient;
import com.expensetracker.model.ExpenseRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Service
public class ExpenseService {

    private final GoogleSheetsClient googleSheetsClient;

    public ExpenseService(GoogleSheetsClient googleSheetsClient) {
        this.googleSheetsClient = googleSheetsClient;
    }

    public void createExpense(ExpenseRequest request) {
        validate(request);
        googleSheetsClient.appendExpense(request.date(), request.description(), request.amount());
    }

    private void validate(ExpenseRequest request) {
        if (request.date() == null || request.date().isBlank()) {
            throw new ValidationException("Date is required");
        }
        try {
            LocalDate.parse(request.date());
        } catch (DateTimeParseException e) {
            throw new ValidationException("Date must be in YYYY-MM-DD format");
        }
        if (request.description() == null || request.description().isBlank()) {
            throw new ValidationException("Description is required");
        }
        if (request.description().length() > 255) {
            throw new ValidationException("Description must be at most 255 characters");
        }
        if (request.amount() == null) {
            throw new ValidationException("Amount is required");
        }
        if (request.amount() <= 0) {
            throw new ValidationException("Amount must be greater than 0");
        }
    }
}
