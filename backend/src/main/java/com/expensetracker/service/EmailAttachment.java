package com.expensetracker.service;

/** Attachment email yang akan dikirim ke alur Scan Struk. */
public record EmailAttachment(byte[] content, String filename, String contentType) {
}
