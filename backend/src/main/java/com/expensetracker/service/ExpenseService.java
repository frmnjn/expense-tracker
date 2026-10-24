package com.expensetracker.service;

import com.expensetracker.data.BudgetRepository;
import com.expensetracker.data.CategoryData;
import com.expensetracker.data.CategoryRepository;
import com.expensetracker.data.ExpenseData;
import com.expensetracker.data.ExpenseRepository;
import com.expensetracker.data.InvoiceData;
import com.expensetracker.data.TopUpRepository;
import com.expensetracker.data.VoiceNoteData;
import com.expensetracker.model.BatchExpenseItem;
import com.expensetracker.model.BatchExpenseRequest;
import com.expensetracker.model.BudgetCreateRequest;
import com.expensetracker.model.BudgetSummary;
import com.expensetracker.model.BudgetUpdateRequest;
import com.expensetracker.model.CategoryCreateRequest;
import com.expensetracker.model.CategoryOption;
import com.expensetracker.model.CategorySummary;
import com.expensetracker.model.CategoryUpdateRequest;
import com.expensetracker.model.CategoriesResponse;
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

    private static final int MAX_DESCRIPTION_LENGTH = 10000;

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;
    private final TopUpRepository topUpRepository;
    private final InvoiceService invoiceService;
    private final VoiceNoteService voiceNoteService;
    private final NotificationService notificationService;
    private final PeriodService periodService;

    public ExpenseService(BudgetRepository budgetRepository,
                          CategoryRepository categoryRepository,
                          ExpenseRepository expenseRepository,
                          TopUpRepository topUpRepository,
                          InvoiceService invoiceService,
                          VoiceNoteService voiceNoteService,
                          NotificationService notificationService,
                          PeriodService periodService) {
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
        this.expenseRepository = expenseRepository;
        this.topUpRepository = topUpRepository;
        this.invoiceService = invoiceService;
        this.voiceNoteService = voiceNoteService;
        this.notificationService = notificationService;
        this.periodService = periodService;
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
        long alertThreshold = requireAlertThreshold(request.alertThreshold());
        String description = requireDescription(request.description());
        try {
            budgetRepository.create(request.name().trim(), balance, alertThreshold, description);
        } catch (IllegalStateException e) {
            throw new ConflictException("Budget already exists");
        }
        notificationService.sendBudgetCreated(request.name().trim(), balance, alertThreshold);
    }

    private static String requireDescription(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > 500) {
            throw new ValidationException("Description must be at most 500 characters");
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static long requireAlertThreshold(Long value) {
        if (value != null && value < 0) {
            throw new ValidationException("Alert threshold must be greater than or equal to 0");
        }
        return value == null ? 0 : value;
    }

    @Transactional
    public void deleteBudget(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Name is required");
        }
        if (!budgetRepository.softDelete(name)) {
            throw new NotFoundException("Budget not found");
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
        Long alertThreshold = request.alertThreshold();
        if (alertThreshold != null && alertThreshold < 0) {
            throw new ValidationException("Alert threshold must be greater than or equal to 0");
        }
        String description = requireDescription(request.description());
        try {
            if (!budgetRepository.update(oldName.trim(), request.name().trim(), request.balance(),
                    alertThreshold, description)) {
                throw new NotFoundException("Budget not found");
            }
        } catch (IllegalStateException e) {
            throw new ConflictException("Budget already exists");
        }
    }

    public CategoriesResponse getCategories(String budgetName) {
        Long budgetId = requireBudgetId(budgetName);
        return new CategoriesResponse(categoryRepository.findByBudgetId(budgetId).stream()
                .map(c -> new CategoryOption(c.id(), c.name(), c.description()))
                .toList());
    }

    @Transactional
    public void createCategory(CategoryCreateRequest request) {
        if (request.budgetName() == null || request.budgetName().isBlank()) {
            throw new ValidationException("Budget is required");
        }
        String name = requireCategoryName(request.name());
        String description = requireDescription(request.description());
        Long budgetId = requireBudgetId(request.budgetName());
        try {
            categoryRepository.create(budgetId, name, description);
        } catch (IllegalStateException e) {
            throw new ConflictException("Category already exists");
        }
    }

    @Transactional
    public void updateCategory(long id, CategoryUpdateRequest request) {
        String name = requireCategoryName(request.name());
        String description = requireDescription(request.description());
        CategoryData existing = categoryRepository.findById(id);
        if (existing == null || !existing.isActive()) {
            throw new NotFoundException("Category not found");
        }
        try {
            if (!categoryRepository.update(id, name, description)) {
                throw new NotFoundException("Category not found");
            }
        } catch (IllegalStateException e) {
            throw new ConflictException("Category already exists");
        }
    }

    @Transactional
    public void deleteCategory(long id) {
        CategoryData existing = categoryRepository.findById(id);
        if (existing == null || !existing.isActive()) {
            throw new NotFoundException("Category not found");
        }
        categoryRepository.softDelete(id);
    }

    private static String requireCategoryName(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("Name is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > 255) {
            throw new ValidationException("Name must be at most 255 characters");
        }
        return trimmed;
    }

    /** Resolve category_id; null bila categoryName kosong (Uncategorized). */
    private Long requireCategoryId(Long budgetId, String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return null;
        }
        Long id = categoryRepository.findIdByBudgetAndName(budgetId, categoryName.trim());
        if (id == null) {
            throw new NotFoundException("Category not found for budget: " + categoryName);
        }
        return id;
    }

    @Transactional
    public String createExpense(ExpenseRequest request) {
        validate(request);
        LocalDateTime dateTime = PeriodSheetName.parseLenient(request.dateTime());
        Long budgetId = requireBudgetId(request.budget());
        Long categoryId = requireCategoryId(budgetId, request.category());
        validateVoiceNoteForSubmit(request.voiceNoteId());
        String id = UUID.randomUUID().toString();
        PaydayCalendar calendar = periodService.calendar();
        String period = calendar.forDate(dateTime.toLocalDate());
        LocalDate periodStart = calendar.periodStart(dateTime.toLocalDate());
        expenseRepository.insert(
                id,
                period,
                periodStart,
                dateTime,
                budgetId,
                categoryId,
                request.name(),
                request.amount(),
                request.description());
        if (request.invoiceId() != null && !request.invoiceId().isBlank()) {
            attachInvoiceToExpense(id, request.invoiceId(), period);
            invoiceService.markSubmitted(request.invoiceId());
        }
        if (request.voiceNoteId() != null && !request.voiceNoteId().isBlank()) {
            expenseRepository.attachVoiceNote(id, request.voiceNoteId());
            voiceNoteService.markSubmitted(request.voiceNoteId(), dateTime.toLocalDate());
        }
        budgetRepository.adjustBalance(request.budget(), -request.amount());
        notificationService.sendExpenseCreated(request.name(), request.budget(), request.amount(),
                dateTime.format(PeriodSheetName.FORMATTER), budgetRepository.getBalance(request.budget()));
        checkBudgetAlert(request.budget());
        return id;
    }

    private void checkBudgetAlert(String budget) {
        long threshold = budgetRepository.getAlertThreshold(budget);
        if (threshold > 0 && budgetRepository.getBalance(budget) < threshold) {
            notificationService.sendBudgetAlert(budget, budgetRepository.getBalance(budget), threshold);
        }
    }

    /**
     * Membuat banyak expense (hasil split struk) dalam satu transaksi. Semua
     * memakai dateTime yang sama; saldo budget di-adjust per-budget secara
     * agregat; invoice ditandai SUBMITTED setelah berhasil.
     */
    @Transactional
    public int createExpenseBatch(BatchExpenseRequest request) {
        if (request.dateTime() == null || request.dateTime().isBlank()) {
            throw new ValidationException("DateTime is required");
        }
        LocalDateTime dateTime;
        try {
            dateTime = PeriodSheetName.parseLenient(request.dateTime());
        } catch (DateTimeParseException e) {
            throw new ValidationException("DateTime must be in yyyy-MM-dd HH:mm format");
        }
        if (request.groups() == null || request.groups().isEmpty()) {
            throw new ValidationException("At least one expense is required");
        }
        String period = periodService.forDate(dateTime.toLocalDate());
        LocalDate periodStart = periodService.periodStart(dateTime.toLocalDate());

        if (request.invoiceId() != null && !request.invoiceId().isBlank()) {
            invoiceService.requireUsableForExpense(request.invoiceId());
            // Ikutkan periode invoice ke tanggal yang dipilih (bisa hasil edit user),
            // agar periode invoice konsisten dengan tanggal expense.
            invoiceService.updatePeriod(request.invoiceId(), dateTime.toLocalDate());
        }
        validateVoiceNoteForSubmit(request.voiceNoteId());

        Map<String, Long> totalByBudget = new LinkedHashMap<>();
        for (BatchExpenseItem item : request.groups()) {
            validateItem(item);
            requireBudgetId(item.budget());
            totalByBudget.merge(item.budget(), item.amount(), Long::sum);
        }

        for (BatchExpenseItem item : request.groups()) {
            Long budgetId = requireBudgetId(item.budget());
            Long categoryId = requireCategoryId(budgetId, item.category());
            String id = UUID.randomUUID().toString();
            expenseRepository.insert(id, period, periodStart, dateTime, budgetId, categoryId, item.name(),
                    item.amount(), item.description());
            if (request.invoiceId() != null && !request.invoiceId().isBlank()) {
                expenseRepository.attachInvoice(id, request.invoiceId());
            }
            if (request.voiceNoteId() != null && !request.voiceNoteId().isBlank()) {
                expenseRepository.attachVoiceNote(id, request.voiceNoteId());
            }
        }

        for (Map.Entry<String, Long> entry : totalByBudget.entrySet()) {
            budgetRepository.adjustBalance(entry.getKey(), -entry.getValue());
        }

        if (request.invoiceId() != null && !request.invoiceId().isBlank()) {
            invoiceService.markSubmitted(request.invoiceId());
        }
        if (request.voiceNoteId() != null && !request.voiceNoteId().isBlank()) {
            voiceNoteService.markSubmitted(request.voiceNoteId(), dateTime.toLocalDate());
        }

        long total = totalByBudget.values().stream().mapToLong(Long::longValue).sum();
        notificationService.sendBatchCreated(total, request.groups().size(),
                dateTime.format(PeriodSheetName.FORMATTER), totalByBudget);
        for (String budget : totalByBudget.keySet()) {
            checkBudgetAlert(budget);
        }
        return request.groups().size();
    }

    private void validateItem(BatchExpenseItem item) {
        if (item.name() == null || item.name().isBlank()) {
            throw new ValidationException("Name is required");
        }
        if (item.name().length() > 255) {
            throw new ValidationException("Name must be at most 255 characters");
        }
        if (item.budget() == null || item.budget().isBlank()) {
            throw new ValidationException("Budget is required");
        }
        if (item.amount() == null) {
            throw new ValidationException("Amount is required");
        }
        if (item.amount() <= 0) {
            throw new ValidationException("Amount must be greater than 0");
        }
        if (item.description() != null && item.description().length() > MAX_DESCRIPTION_LENGTH) {
            throw new ValidationException("Description must be at most " + MAX_DESCRIPTION_LENGTH + " characters");
        }
    }

    private void attachInvoiceToExpense(String expenseId, String invoiceId, String period) {
        InvoiceData invoice = invoiceService.requireInvoice(invoiceId);
        invoiceService.requireUsableForExpense(invoiceId);
        if (!invoice.period().equals(period)) {
            throw new ValidationException("Invoice is not in the same period as the expense");
        }
        expenseRepository.attachInvoice(expenseId, invoiceId);
    }

    public PeriodsResponse getPeriods() {
        return periodService.buildPeriods(expenseRepository.getPeriods());
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
        Map<String, Map<String, Long>> amountByCategory = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> countByCategory = new LinkedHashMap<>();
        for (ExpenseData expense : expenses) {
            total += expense.amount();
            amountByBudget.merge(expense.budgetName(), expense.amount(), Long::sum);
            countByBudget.merge(expense.budgetName(), 1, Integer::sum);
            String category = expense.categoryName() == null || expense.categoryName().isBlank()
                    ? "Uncategorized"
                    : expense.categoryName();
            amountByCategory.computeIfAbsent(expense.budgetName(), k -> new LinkedHashMap<>())
                    .merge(category, expense.amount(), Long::sum);
            countByCategory.computeIfAbsent(expense.budgetName(), k -> new LinkedHashMap<>())
                    .merge(category, 1, Integer::sum);
        }
        List<BudgetSummary> list = amountByBudget.entrySet().stream()
                .map(e -> new BudgetSummary(e.getKey(), e.getValue(),
                        countByBudget.getOrDefault(e.getKey(), 0),
                        buildCategorySummaries(amountByCategory.get(e.getKey()), countByCategory.get(e.getKey()))))
                .sorted(Comparator.comparingLong(BudgetSummary::amount).reversed())
                .toList();
        return new SummaryResponse(period, total, expenses.size(), list);
    }

    private static List<CategorySummary> buildCategorySummaries(Map<String, Long> amounts, Map<String, Integer> counts) {
        if (amounts == null) {
            return List.of();
        }
        return amounts.entrySet().stream()
                .map(e -> new CategorySummary(e.getKey(), e.getValue(), counts.getOrDefault(e.getKey(), 0)))
                .sorted(Comparator.comparingLong(CategorySummary::amount).reversed())
                .toList();
    }

    public TopUpsResponse getTopUps() {
        return new TopUpsResponse(topUpRepository.getTopUps());
    }

    public TrendResponse getTrend(int months) {
        if (months < 1) {
            throw new ValidationException("Months must be greater than 0");
        }
        int safeMonths = Math.min(months, 24);
        PaydayCalendar calendar = periodService.calendar();
        List<String> periods = new ArrayList<>();
        String period = periodService.currentPeriod();
        for (int i = 0; i < safeMonths; i++) {
            periods.add(period);
            period = calendar.previousPeriod(period);
        }
        Collections.reverse(periods);

        List<TrendPoint> points = new ArrayList<>();
        for (String item : periods) {
            points.add(new TrendPoint(item,
                    expenseRepository.totalForPeriod(item),
                    expenseRepository.countForPeriod(item)));
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
        if (request.description() != null && request.description().length() > MAX_DESCRIPTION_LENGTH) {
            throw new ValidationException("Description must be at most " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        String dateTime = request.dateTime();
        if (dateTime == null || dateTime.isBlank()) {
            dateTime = LocalDateTime.now().format(PeriodSheetName.FORMATTER);
        } else {
            try {
                PeriodSheetName.parseLenient(dateTime);
            } catch (DateTimeParseException e) {
                throw new ValidationException("DateTime must be in yyyy-MM-dd HH:mm format");
            }
        }
        Long budgetId = requireBudgetId(request.budget());
        topUpRepository.insert(
                UUID.randomUUID().toString(),
                PeriodSheetName.parseLenient(dateTime),
                budgetId,
                request.amount(),
                request.description());
        budgetRepository.adjustBalance(request.budget(), request.amount());
        notificationService.sendTopUp(request.budget(), request.amount(), budgetRepository.getBalance(request.budget()));
    }

    @Transactional
    public void updateExpense(String id, ExpenseRequest request) {        validate(request);
        ExpenseData current = requireExpense(id);
        LocalDateTime dateTime = PeriodSheetName.parseLenient(request.dateTime());
        Long newBudgetId = requireBudgetId(request.budget());
        Long newCategoryId = requireCategoryId(newBudgetId, request.category());

        if (!current.budgetName().equals(request.budget())) {
            budgetRepository.adjustBalance(current.budgetName(), current.amount());
            budgetRepository.adjustBalance(request.budget(), -request.amount());
        } else if (request.amount() != current.amount()) {
            budgetRepository.adjustBalance(current.budgetName(), current.amount() - request.amount());
        }

        expenseRepository.update(
                id,
                periodService.forDate(dateTime.toLocalDate()),
                periodService.periodStart(dateTime.toLocalDate()),
                dateTime,
                newBudgetId,
                newCategoryId,
                request.name(),
                request.amount(),
                request.description());

        if (request.invoiceId() != null && !request.invoiceId().isBlank()
                && !request.invoiceId().equals(current.invoiceId())) {
            String oldInvoiceId = current.invoiceId();
            if (oldInvoiceId != null && !oldInvoiceId.isBlank()) {
                expenseRepository.detachPhoto(id);
                invoiceService.deleteIfUnused(oldInvoiceId);
            }
            attachInvoiceToExpense(id, request.invoiceId(), periodService.forDate(dateTime.toLocalDate()));
            invoiceService.markSubmitted(request.invoiceId());
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
        if (current.invoiceId() != null && !current.invoiceId().isBlank()) {
            invoiceService.deleteIfUnused(current.invoiceId());
        }
        if (current.voiceNoteId() != null) {
            voiceNoteService.deleteIfUnused(current.voiceNoteId());
        }
    }

    @Transactional
    public void attachPhoto(String id, MultipartFile file) {
        ExpenseData expense = requireExpense(id);
        String period = expense.period();
        LocalDate periodStart = periodService.periodStart(
                PeriodSheetName.parseLenient(expense.dateTime()).toLocalDate());
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

    public ExpenseResponse getExpense(String id) {
        return toResponse(requireExpense(id));
    }

    private ExpenseData requireExpense(String id) {
        if (id == null || id.isBlank()) {
            throw new ValidationException("Expense id is required");
        }
        ExpenseData expense = expenseRepository.findById(id);
        if (expense == null) {
            throw new NotFoundException("Expense not found");
        }
        return expense;
    }

    private Long requireBudgetId(String name) {
        Long id = budgetRepository.findIdByName(name);
        if (id == null) {
            throw new NotFoundException("Budget not found: " + name);
        }
        return id;
    }

    private ExpenseResponse toResponse(ExpenseData expense) {
        return new ExpenseResponse(
                expense.id(),
                expense.dateTime(),
                expense.name(),
                expense.budgetName(),
                expense.categoryName(),
                expense.amount(),
                expense.description(),
                expense.hasPhoto(),
                expense.photoType(),
                expense.photoName(),
                expense.voiceNoteId());
    }

    /**
     * Validasi voice note yang akan dijadikan sumber expense. Hanya note yang
     * sudah selesai dianalisis (TO_REVIEW) dan belum dipakai yang boleh disubmit.
     */
    private void validateVoiceNoteForSubmit(String voiceNoteId) {
        if (voiceNoteId == null || voiceNoteId.isBlank()) {
            return;
        }
        VoiceNoteData note = voiceNoteService.requireVoiceNote(voiceNoteId);
        if (!VoiceNoteStatus.TO_REVIEW.value().equals(note.status())) {
            throw new ConflictException("Voice note is not ready for review or already submitted");
        }
    }

    private void validate(ExpenseRequest request) {
        if (request.dateTime() == null || request.dateTime().isBlank()) {
            throw new ValidationException("DateTime is required");
        }
        try {
            PeriodSheetName.parseLenient(request.dateTime());
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
        if (request.description() != null && request.description().length() > MAX_DESCRIPTION_LENGTH) {
            throw new ValidationException("Description must be at most " + MAX_DESCRIPTION_LENGTH + " characters");
        }
    }
}
