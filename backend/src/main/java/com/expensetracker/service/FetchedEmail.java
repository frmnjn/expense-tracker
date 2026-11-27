package com.expensetracker.service;

/** Email yang diambil ulang dari IMAP berdasarkan Message-ID (untuk retry). */
public record FetchedEmail(String sender, String subject, String body, boolean html) {

    public FetchedEmail(String sender, String subject, String body) {
        this(sender, subject, body, true);
    }
}
