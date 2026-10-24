package com.expensetracker.data;

import java.time.LocalDate;

/** Baris minimal untuk menghitung ulang periode berdasarkan tanggal acuan. */
public record PeriodDateRow(String id, LocalDate date) {
}
