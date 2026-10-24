package com.expensetracker.service;

import com.expensetracker.data.PaydayOverrideRepository;
import com.expensetracker.model.PeriodRange;
import com.expensetracker.model.PeriodsResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class PeriodService {

    private static final int CALENDAR_MONTHS_AHEAD = 6;

    private final PaydayOverrideRepository paydayOverrideRepository;

    public PeriodService(PaydayOverrideRepository paydayOverrideRepository) {
        this.paydayOverrideRepository = paydayOverrideRepository;
    }

    public PaydayCalendar calendar() {
        return new PaydayCalendar(paydayOverrideRepository.findAll());
    }

    public String forDate(LocalDate date) {
        return calendar().forDate(date);
    }

    public LocalDate periodStart(LocalDate date) {
        return calendar().periodStart(date);
    }

    public String currentPeriod() {
        return forDate(LocalDate.now());
    }

    public PeriodsResponse buildPeriods(List<String> storedPeriods) {
        PaydayCalendar calendar = calendar();
        String current = calendar.forDate(LocalDate.now());

        List<String> periods = new ArrayList<>();
        periods.add(current);
        for (String period : storedPeriods) {
            if (!periods.contains(period)) {
                periods.add(period);
            }
        }

        YearMonth now = YearMonth.now();
        Set<YearMonth> months = new LinkedHashSet<>();
        months.add(now.minusMonths(1));
        for (int i = 0; i <= CALENDAR_MONTHS_AHEAD; i++) {
            months.add(now.plusMonths(i));
        }
        for (String period : periods) {
            PaydayCalendar.parsePeriod(period).ifPresent(month -> {
                months.add(month);
                months.add(month.minusMonths(1));
            });
        }

        List<PeriodRange> ranges = months.stream()
                .sorted()
                .map(month -> {
                    String name = PaydayCalendar.periodName(month);
                    String next = PaydayCalendar.periodName(month.plusMonths(1));
                    return new PeriodRange(
                            name,
                            calendar.paydayForPeriod(name).toString(),
                            calendar.paydayForPeriod(next).toString(),
                            calendar.isOverridden(name));
                })
                .toList();

        return new PeriodsResponse(periods, current, ranges);
    }
}
