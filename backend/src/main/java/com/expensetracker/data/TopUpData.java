package com.expensetracker.data;

import java.time.LocalDateTime;

public record TopUpData(String id, String budgetName, long amount, LocalDateTime dateTime) {
}
