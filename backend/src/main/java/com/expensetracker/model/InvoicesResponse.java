package com.expensetracker.model;

import java.util.List;

public record InvoicesResponse(List<InvoiceResponse> invoices) {
}
