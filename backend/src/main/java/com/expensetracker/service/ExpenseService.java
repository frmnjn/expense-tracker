package com.expensetracker.service;

import com.expensetracker.google.GoogleSheetsClient;
import com.expensetracker.model.ExpenseRef;
import com.expensetracker.model.ExpenseRequest;
import com.expensetracker.model.ExpenseResponse;
import com.expensetracker.model.ExpensesResponse;
import com.expensetracker.model.OptionsResponse;
import com.expensetracker.model.PeriodsResponse;
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
        return new OptionsResponse(googleSheetsClient.getOptions(googleSheetsClient.getBudgetSheet()));
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
                request.amount(),
                request.description());
        googleSheetsClient.decrementBudget(request.budget(), request.amount());
    }

    public PeriodsResponse getPeriods() {
        return new PeriodsResponse(googleSheetsClient.getPeriodSheetTitles());
    }

    public ExpensesResponse getExpenses(String period) {
        if (period == null || period.isBlank()) {
            throw new ValidationException("Period is required");
        }
        return new ExpensesResponse(googleSheetsClient.getExpenses(period));
    }

    public void updateExpense(String id, ExpenseRequest request) {
        validate(request);
        ExpenseRef ref = requireExpense(id);
        ExpenseResponse current = ref.expense();

        String newBudget = request.budget();
        long newAmount = request.amount();

        if (!current.budget().equals(newBudget)) {
            googleSheetsClient.adjustBudgetBalance(current.budget(), current.amount());
            googleSheetsClient.adjustBudgetBalance(newBudget, -newAmount);
        } else if (newAmount != current.amount()) {
            googleSheetsClient.adjustBudgetBalance(current.budget(), current.amount() - newAmount);
        }

        googleSheetsClient.updateExpenseRow(
                ref.sheetName(),
                ref.rowIndex(),
                request.dateTime(),
                request.name(),
                newBudget,
                newAmount,
                request.description());
    }

    public void deleteExpense(String id) {
        ExpenseRef ref = requireExpense(id);
        googleSheetsClient.adjustBudgetBalance(ref.expense().budget(), ref.expense().amount());
        googleSheetsClient.softDeleteExpense(ref.sheetName(), ref.rowIndex());
    }

    private ExpenseRef requireExpense(String id) {
        if (id == null || id.isBlank()) {
            throw new ValidationException("Expense id is required");
        }
        ExpenseRef ref = googleSheetsClient.findExpense(id);
        if (ref == null) {
            throw new ValidationException("Expense not found");
        }
        return ref;
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
