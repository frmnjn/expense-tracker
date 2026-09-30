package com.expensetracker.controller;

import com.expensetracker.data.EmailImportData;
import com.expensetracker.model.ApiResponse;
import com.expensetracker.model.EmailImportResponse;
import com.expensetracker.model.EmailImportsResponse;
import com.expensetracker.model.ExpenseRequest;
import com.expensetracker.service.EmailImportService;
import com.expensetracker.service.EmailInboxService;
import com.expensetracker.service.DuplicateExpenseException;
import com.expensetracker.service.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class EmailImportController {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailImportController.class);

    private final EmailImportService emailImportService;
    private final EmailInboxService emailInboxService;

    public EmailImportController(EmailImportService emailImportService,
                                 EmailInboxService emailInboxService) {
        this.emailImportService = emailImportService;
        this.emailInboxService = emailInboxService;
    }

    @GetMapping("/email-imports")
    public ResponseEntity<ApiResponse> getEmailImports(@RequestParam(value = "status", required = false) String status) {
        try {
            EmailImportsResponse response = new EmailImportsResponse(
                    emailImportService.list(status).stream().map(EmailImportController::toResponse).toList());
            return ResponseEntity.ok(ApiResponse.ok(response));
        } catch (ValidationException e) {
            LOGGER.warn("response error: status={} message={}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("internal error getting email imports", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    @PostMapping("/email-imports/{id}/import")
    public ResponseEntity<ApiResponse> importEmail(@PathVariable String id,
                                                   @RequestParam(value = "force", defaultValue = "false") boolean force,
                                                   @RequestBody ExpenseRequest request) {
        try {
            emailImportService.importToExpense(id, request, force);
            return ResponseEntity.ok(ApiResponse.ok());
        } catch (DuplicateExpenseException e) {
            LOGGER.warn("duplicate expense on import: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
        } catch (ValidationException e) {
            LOGGER.warn("response error: status={} message={}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("internal error importing email transaction", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    @PostMapping("/email-imports/{id}/discard")
    public ResponseEntity<ApiResponse> discardEmail(@PathVariable String id) {
        try {
            emailImportService.discard(id);
            return ResponseEntity.ok(ApiResponse.ok());
        } catch (ValidationException e) {
            LOGGER.warn("response error: status={} message={}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("internal error discarding email transaction", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    @PostMapping("/email-imports/{id}/retry")
    public ResponseEntity<ApiResponse> retryEmail(@PathVariable String id) {
        try {
            emailImportService.retry(id);
            return ResponseEntity.ok(ApiResponse.ok());
        } catch (ValidationException e) {
            LOGGER.warn("response error: status={} message={}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("internal error retrying email transaction", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    @PostMapping("/email-imports/poll")
    public ResponseEntity<ApiResponse> pollEmails() {
        try {
            if (!emailInboxService.isEnabled()) {
                throw new ValidationException("Inbox email belum dikonfigurasi");
            }
            int count = emailInboxService.poll();
            return ResponseEntity.ok(ApiResponse.ok(Map.of("count", count)));
        } catch (ValidationException e) {
            LOGGER.warn("response error: status={} message={}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("internal error polling inbox", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    private static EmailImportResponse toResponse(EmailImportData data) {
        return new EmailImportResponse(
                data.id(), data.sender(), data.subject(), data.receivedAt(), data.transactionAt(),
                data.merchant(), data.amount(), data.description(), data.suggestedBudget(), data.suggestedCategory(),
                data.parseMethod(), data.status(), data.errorMessage(), data.expenseId());
    }
}
