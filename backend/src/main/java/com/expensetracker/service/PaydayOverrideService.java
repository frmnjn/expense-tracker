package com.expensetracker.service;

import com.expensetracker.data.ExpenseRepository;
import com.expensetracker.data.InvoiceRepository;
import com.expensetracker.data.PaydayOverrideRepository;
import com.expensetracker.data.PeriodDateRow;
import com.expensetracker.data.VoiceNoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;

@Service
public class PaydayOverrideService {

    private static final int MAX_MONTHS_AHEAD = 6;

    private final PaydayOverrideRepository paydayOverrideRepository;
    private final ExpenseRepository expenseRepository;
    private final InvoiceRepository invoiceRepository;
    private final VoiceNoteRepository voiceNoteRepository;
    private final PeriodService periodService;

    public PaydayOverrideService(PaydayOverrideRepository paydayOverrideRepository,
                                 ExpenseRepository expenseRepository,
                                 InvoiceRepository invoiceRepository,
                                 VoiceNoteRepository voiceNoteRepository,
                                 PeriodService periodService) {
        this.paydayOverrideRepository = paydayOverrideRepository;
        this.expenseRepository = expenseRepository;
        this.invoiceRepository = invoiceRepository;
        this.voiceNoteRepository = voiceNoteRepository;
        this.periodService = periodService;
    }

    @Transactional
    public void save(String period, LocalDate paydayDate) {
        YearMonth month = requireEditablePeriod(period);
        if (paydayDate == null) {
            throw new ValidationException("Payday date is required");
        }
        if (!YearMonth.from(paydayDate).equals(month)) {
            throw new ValidationException("Payday date must be in the same month as the period");
        }
        paydayOverrideRepository.upsert(period, paydayDate);
        reclassifyAll();
    }

    @Transactional
    public void delete(String period) {
        YearMonth month = PaydayCalendar.parsePeriod(period)
                .orElseThrow(() -> new ValidationException("Invalid period: " + period));
        YearMonth now = YearMonth.now();
        if (month.isBefore(now) || month.isAfter(now.plusMonths(MAX_MONTHS_AHEAD))) {
            throw new ValidationException("Period is outside the editable range");
        }
        paydayOverrideRepository.delete(period);
        reclassifyAll();
    }

    private YearMonth requireEditablePeriod(String period) {
        YearMonth month = PaydayCalendar.parsePeriod(period)
                .orElseThrow(() -> new ValidationException("Invalid period: " + period));
        YearMonth now = YearMonth.now();
        if (month.isBefore(now) || month.isAfter(now.plusMonths(MAX_MONTHS_AHEAD))) {
            throw new ValidationException("Period is outside the editable range");
        }
        return month;
    }

    private void reclassifyAll() {
        PaydayCalendar calendar = periodService.calendar();
        for (PeriodDateRow row : expenseRepository.findAllPeriodDates()) {
            String period = calendar.forDate(row.date());
            expenseRepository.updatePeriod(row.id(), period, calendar.paydayForPeriod(period));
        }
        for (PeriodDateRow row : invoiceRepository.findAllPeriodDates()) {
            String period = calendar.forDate(row.date());
            invoiceRepository.updatePeriod(row.id(), period, calendar.paydayForPeriod(period));
        }
        for (PeriodDateRow row : voiceNoteRepository.findAllPeriodDates()) {
            String period = calendar.forDate(row.date());
            voiceNoteRepository.updatePeriod(row.id(), period, calendar.paydayForPeriod(period));
        }
    }
}
