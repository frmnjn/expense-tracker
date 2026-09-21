package com.expensetracker.service;

/**
 * Dilempar saat import email mendeteksi sudah ada expense dengan nominal persis
 * sama pada periode yang sama. User bisa mengirim ulang dengan force untuk
 * tetap import (nominal sama bisa saja sah berulang).
 */
public class DuplicateExpenseException extends RuntimeException {

    public DuplicateExpenseException(String message) {
        super(message);
    }
}
