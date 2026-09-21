package com.expensetracker.service;

import java.time.LocalDateTime;

/**
 * Hasil ekstraksi satu email transaksi. transactionAt bisa null bila tanggal
 * tidak terbaca (pemanggil memakai waktu saat ini sebagai fallback).
 */
public record ParsedTransaction(
        String merchant,
        long amount,
        LocalDateTime transactionAt,
        String suggestedBudget,
        String parseMethod) {
}
