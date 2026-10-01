package com.expensetracker.service;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Hasil ekstraksi satu email transaksi. transactionAt bisa null bila tanggal
 * tidak terbaca (pemanggil memakai waktu saat ini sebagai fallback).
 *
 * <p>Untuk transaksi mata uang asing, {@code amount} adalah nilai IDR hasil
 * konversi; {@code currency}/{@code originalAmount}/{@code exchangeRate} menyimpan
 * detail aslinya untuk catatan.</p>
 */
public record ParsedTransaction(
        String merchant,
        long amount,
        LocalDateTime transactionAt,
        String suggestedBudget,
        String suggestedCategory,
        String parseMethod,
        String currency,
        Double originalAmount,
        Double exchangeRate,
        String exchangeDate) {

    public ParsedTransaction(String merchant, long amount, LocalDateTime transactionAt, String suggestedBudget,
                             String suggestedCategory, String parseMethod) {
        this(merchant, amount, transactionAt, suggestedBudget, suggestedCategory, parseMethod,
                null, null, null, null);
    }

    public ParsedTransaction(String merchant, long amount, LocalDateTime transactionAt, String suggestedBudget,
                             String parseMethod) {
        this(merchant, amount, transactionAt, suggestedBudget, null, parseMethod, null, null, null, null);
    }

    /** Catatan konversi, mis. "USD 0,45 @ 15.888 = Rp7.150". Null bila bukan konversi. */
    public String conversionNote() {
        if (currency == null || currency.isBlank() || "IDR".equalsIgnoreCase(currency)
                || originalAmount == null || exchangeRate == null) {
            return null;
        }
        NumberFormat idr = NumberFormat.getCurrencyInstance(Locale.of("id", "ID"));
        NumberFormat decimal = NumberFormat.getNumberInstance(Locale.of("id", "ID"));
        return currency.toUpperCase(Locale.ROOT) + " " + decimal.format(originalAmount)
                + " @ " + decimal.format(exchangeRate)
                + " = " + idr.format(amount);
    }
}
