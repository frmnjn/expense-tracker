package com.expensetracker.service;

/**
 * Email valid tapi bukan transaksi pengeluaran (mis. promo/transfer masuk).
 * Dipakai agar baris ditandai DISCARDED, bukan FAILED.
 */
public class NotExpenseException extends ValidationException {

    public NotExpenseException(String message) {
        super(message);
    }
}
