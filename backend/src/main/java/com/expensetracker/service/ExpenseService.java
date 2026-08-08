package com.expensetracker.service;

import com.expensetracker.data.BudgetRepository;
import com.expensetracker.data.ExpenseData;
import com.expensetracker.data.ExpenseRepository;
import com.expensetracker.data.InvoiceData;
import com.expensetracker.data.TopUpRepository;
import com.expensetracker.model.BudgetCreateRequest;
import com.expensetracker.model.BudgetSummary;
import com.expensetracker.model.BudgetUpdateRequest;
import com.expensetracker.model.ExpenseRequest;
import com.expensetracker.model.ExpenseResponse;
import com.expensetracker.model.ExpensesResponse;
import com.expensetracker.model.OptionsResponse;
import com.expensetracker.model.PeriodsResponse;
import com.expensetracker.model.SummaryResponse;
import com.expensetracker.model.TopUpRequest;
import com.expensetracker.model.TopUpsResponse;
import com.expensetracker.model.TrendPoint;
import com.expensetracker.model.TrendResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ExpenseService {

    private final BudgetRepository budgetRepository;
    private final ExpenseRepository expenseRepository;
    private final TopUpRepository topUpRepository;
    private final InvoiceService invoiceService;

    public ExpenseService(BudgetRepository budgetRepository,
                          ExpenseRepository expenseRepository,
                          TopUpRepository topUpRepository,
                          InvoiceService invoiceService) {
        this.budgetRepository = budgetRepository;
        this.expenseRepository = expenseRepository;
        this.topUpRepository = topUpRepository;
        this.invoiceService = invoiceService;
    }

    public OptionsResponse getOptions() {
        return new OptionsResponse(budgetRepository.getOptions());
    }

    @Transactional
    public void createBudget(BudgetCreateRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ValidationException("Name is required");
        }
        if (request.name().length() > 255) {
            throw new ValidationException("Name must be at most 255 characters");
        }
        long balance = request.balance() == null ? 0 : request.balance();
        try {
            budgetRepository.create(request.name().trim(), balance);
        } catch (IllegalStateException e) {
            throw new ValidationException("Budget already exists");
        }
    }

    @Transactional
    public void deleteBudget(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Name is required");
        }
        if (!budgetRepository.softDelete(name)) {
            throw new ValidationException("Budget not found");
        }
    }

    @Transactional
    public void updateBudget(String oldName, BudgetUpdateRequest request) {
        if (oldName == null || oldName.isBlank()) {
            throw new ValidationException("Name is required");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new ValidationException("Name is required");
        }
        if (request.name().length() > 255) {
            throw new ValidationException("Name must be at most 255 characters");
        }
        try {
            if (!budgetRepository.update(oldName.trim(), request.name().trim(), request.balance())) {
                throw new ValidationException("Budget not found");
            }
        } catch (IllegalStateException e) {
            throw new ValidationException("Budget already exists");
        }
    }

    @Transactional
    public String createExpense(ExpenseRequest request) {
        validate(request);
        LocalDateTime dateTime = LocalDateTime.parse(request.dateTime(), PeriodSheetName.FORMATTER);
        Long budgetId = requireBudgetId(request.budget());
        String id = UUID.randomUUID().toString();
        String period = PeriodSheetName.forDate(dateTime.toLocalDate());
        LocalDate periodStart = PeriodSheetName.periodStart(dateTime.toLocalDate());
        expenseRepository.insert(
                id,
                period,
                periodStart,
                dateTime,
                budgetId,
                request.name(),
                request.amount(),
                request.description());
        if (request.invoiceId() != null && !request.invoiceId().isBlank()) {
            attachInvoiceToExpense(id, request.invoiceId(), period);
        }
        budgetRepository.adjustBalance(request.budget(), -request.amount());
        return id;
    }

    private void attachInvoiceToExpense(String expenseId, String invoiceId, String period) {
        InvoiceData invoice = invoiceService.requireInvoice(invoiceId);
        if (!invoice.period().equals(period)) {
            throw new ValidationException("Invoice is not in the same period as the expense");
        }
        expenseRepository.attachInvoice(expenseId, invoiceId);
    }

    public PeriodsResponse getPeriods() {
        return new PeriodsResponse(expenseRepository.getPeriods());
    }

    public ExpensesResponse getExpenses(String period) {
        if (period == null || period.isBlank()) {
            throw new ValidationException("Period is required");
        }
        return new ExpensesResponse(expenseRepository.getExpenses(period).stream()
                .map(this::toResponse)
                .toList());
    }

    public SummaryResponse getSummary(String period) {
        if (period == null || period.isBlank()) {
            throw new ValidationException("Period is required");
        }
        List<ExpenseData> expenses = expenseRepository.getExpenses(period);
        long total = 0;
        Map<String, Long> amountByBudget = new LinkedHashMap<>();
        Map<String, Integer> countByBudget = new LinkedHashMap<>();
        for (ExpenseData expense : expenses) {
            total += expense.amount();
            amountByBudget.merge(expense.budgetName(), expense.amount(), Long::sum);
            countByBudget.merge(expense.budgetName(), 1, Integer::sum);
        }
        List<BudgetSummary> list = amountByBudget.entrySet().stream()
                .map(e -> new BudgetSummary(e.getKey(), e.getValue(),
                        countByBudget.getOrDefault(e.getKey(), 0)))
                .sorted(Comparator.comparingLong(BudgetSummary::amount).reversed())
                .toList();
        return new SummaryResponse(period, total, expenses.size(), list);
    }

    public TopUpsResponse getTopUps() {
        return new TopUpsResponse(topUpRepository.getTopUps());
    }

    public TrendResponse getTrend(int months) {
        if (months < 1) {
            throw new ValidationException("Months must be greater than 0");
        }
        List<String> newest = expenseRepository.getPeriods();
        if (newest.size() > months) {
            newest = newest.subList(0, months);
        }
        List<String> ascending = new ArrayList<>(newest);
        Collections.reverse(ascending);

        List<TrendPoint> points = new ArrayList<>();
        for (String period : ascending) {
            points.add(new TrendPoint(period,
                    expenseRepository.totalForPeriod(period),
                    expenseRepository.countForPeriod(period)));
        }
        return new TrendResponse(points);
    }

    @Transactional
    public void createTopUp(TopUpRequest request) {
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
        String dateTime = request.dateTime();
        if (dateTime == null || dateTime.isBlank()) {
            dateTime = LocalDateTime.now().format(PeriodSheetName.FORMATTER);
        } else {
            try {
                LocalDateTime.parse(dateTime, PeriodSheetName.FORMATTER);
            } catch (DateTimeParseException e) {
                throw new ValidationException("DateTime must be in yyyy-MM-dd HH:mm format");
            }
        }
        Long budgetId = requireBudgetId(request.budget());
        topUpRepository.insert(
                UUID.randomUUID().toString(),
                LocalDateTime.parse(dateTime, PeriodSheetName.FORMATTER),
                budgetId,
                request.amount(),
                request.description());
        budgetRepository.adjustBalance(request.budget(), request.amount());
    }

    @Transactional
    public void updateExpense(String id, ExpenseRequest request) {
        validate(request);
        ExpenseData current = requireExpense(id);
        LocalDateTime dateTime = LocalDateTime.parse(request.dateTime(), PeriodSheetName.FORMATTER);
        Long newBudgetId = requireBudgetId(request.budget());

        if (!current.budgetName().equals(request.budget())) {
            budgetRepository.adjustBalance(current.budgetName(), current.amount());
            budgetRepository.adjustBalance(request.budget(), -request.amount());
        } else if (request.amount() != current.amount()) {
            budgetRepository.adjustBalance(current.budgetName(), current.amount() - request.amount());
        }

        expenseRepository.update(
                id,
                PeriodSheetName.periodStart(dateTime.toLocalDate()),
                dateTime,
                newBudgetId,
                request.name(),
                request.amount(),
                request.description());

        if (request.invoiceId() != null && !request.invoiceId().isBlank()) {
            attachInvoiceToExpense(id, request.invoiceId(), PeriodSheetName.forDate(dateTime.toLocalDate()));
        }
    }

    @Transactional
    public void detachPhoto(String id) {
        ExpenseData expense = requireExpense(id);
        String invoiceId = expense.invoiceId();
        expenseRepository.detachPhoto(id);
        if (invoiceId != null && !invoiceId.isBlank()) {
            invoiceService.deleteIfUnused(invoiceId);
        }
    }

    @Transactional
    public void deleteExpense(String id) {
        ExpenseData current = requireExpense(id);
        budgetRepository.adjustBalance(current.budgetName(), current.amount());
        expenseRepository.softDelete(id);
    }

    @Transactional
    public void attachPhoto(String id, MultipartFile file) {
        ExpenseData expense = requireExpense(id);
        String period = expense.period();
        LocalDate periodStart = PeriodSheetName.periodStart(
                LocalDateTime.parse(expense.dateTime(), PeriodSheetName.FORMATTER).toLocalDate());
        String invoiceId = invoiceService.createInvoice(period, periodStart, file);
        expenseRepository.attachInvoice(id, invoiceId);
    }

    public String getPhotoPath(String id) {
        ExpenseData expense = requireExpense(id);
        if (expense.invoiceId() == null || expense.invoiceId().isBlank()) {
            return null;
        }
        return invoiceService.getInvoicePhotoPath(expense.invoiceId());
    }

    private ExpenseData requireExpense(String id) {
        if (id == null || id.isBlank()) {
            throw new ValidationException("Expense id is required");
        }
        ExpenseData expense = expenseRepository.findById(id);
        if (expense == null) {
            throw new ValidationException("Expense not found");
        }
        return expense;
    }

    private Long requireBudgetId(String name) {
        Long id = budgetRepository.findIdByName(name);
        if (id == null) {
            throw new ValidationException("Budget not found: " + name);
        }
        return id;
    }

    private ExpenseResponse toResponse(ExpenseData expense) {
        return new ExpenseResponse(
                expense.id(),
                expense.dateTime(),
                expense.name(),
                expense.budgetName(),
                expense.amount(),
                expense.description(),
                expense.hasPhoto());
    }

    private void validate(ExpenseRequest request) {
        if (request.dateTime() == null || request.dateTime().isBlank()) {
            throw new ValidationException("DateTime is required");
        }
        try {
            LocalDateTime.parse(request.dateTime(), PeriodSheetName.FORMATTER);
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
