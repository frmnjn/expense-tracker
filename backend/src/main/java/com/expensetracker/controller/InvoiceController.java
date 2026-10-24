package com.expensetracker.controller;

import com.expensetracker.model.ApiResponse;
import com.expensetracker.service.IdempotencyService;
import com.expensetracker.service.InvoiceAnalysisService;
import com.expensetracker.service.InvoiceService;
import com.expensetracker.service.PeriodService;
import com.expensetracker.service.PeriodSheetName;
import com.expensetracker.service.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@RestController
public class InvoiceController {

    private static final Logger LOGGER = LoggerFactory.getLogger(InvoiceController.class);

    private final InvoiceService invoiceService;
    private final InvoiceAnalysisService invoiceAnalysisService;
    private final IdempotencyService idempotencyService;
    private final PeriodService periodService;

    public InvoiceController(InvoiceService invoiceService,
                             InvoiceAnalysisService invoiceAnalysisService,
                             IdempotencyService idempotencyService,
                             PeriodService periodService) {
        this.invoiceService = invoiceService;
        this.invoiceAnalysisService = invoiceAnalysisService;
        this.idempotencyService = idempotencyService;
        this.periodService = periodService;
    }

    @GetMapping("/invoices")
    public ResponseEntity<ApiResponse> getInvoices(@RequestParam(value = "date", required = false) String date,
                                                   @RequestParam(value = "scan", required = false, defaultValue = "false") boolean scan,
                                                   @RequestParam(value = "period", required = false) String period) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(invoiceService.listByDate(date, scan, period)));
        } catch (ValidationException e) {
            LOGGER.warn("response error: status={} message={}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("internal error getting invoices", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    @GetMapping("/invoices/{id}")
    public ResponseEntity<ApiResponse> getInvoice(@PathVariable String id) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(invoiceService.getDetail(id)));
        } catch (ValidationException e) {
            LOGGER.warn("response error: status={} message={}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("internal error getting invoice detail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    @PostMapping(value = "/invoices", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse> createInvoice(@RequestParam("file") MultipartFile file,
                                                     @RequestParam("date") String date,
                                                     @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Optional<ApiResponse> cached = idempotencyService.begin(idempotencyKey);
        if (cached.isPresent()) {
            return ResponseEntity.ok(cached.get());
        }
        try {
            LocalDateTime dateTime = PeriodSheetName.parseLenient(date);
            String period = periodService.forDate(dateTime.toLocalDate());
            LocalDate periodStart = periodService.periodStart(dateTime.toLocalDate());
            String invoiceId = invoiceService.createInvoiceForAi(period, periodStart, file);
            invoiceAnalysisService.trigger(invoiceId);
            ApiResponse response = ApiResponse.ok(Map.of("invoiceId", invoiceId));
            idempotencyService.complete(idempotencyKey, response);
            return ResponseEntity.ok(response);
        } catch (ValidationException e) {
            idempotencyService.abort(idempotencyKey);
            LOGGER.warn("response error: status={} message={}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            idempotencyService.abort(idempotencyKey);
            LOGGER.error("internal error creating invoice", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    @PostMapping("/invoices/{id}/retry")
    public ResponseEntity<ApiResponse> retryAnalysis(@PathVariable String id) {
        try {
            invoiceService.requireInvoice(id);
            invoiceService.setAnalyzing(id);
            invoiceAnalysisService.trigger(id);
            return ResponseEntity.ok(ApiResponse.ok());
        } catch (ValidationException e) {
            LOGGER.warn("response error: status={} message={}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("internal error retrying invoice analysis", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    @DeleteMapping("/invoices/{id}")
    public ResponseEntity<ApiResponse> deleteInvoice(@PathVariable String id) {
        try {
            invoiceService.deleteScanInvoice(id);
            return ResponseEntity.ok(ApiResponse.ok());
        } catch (ValidationException e) {
            LOGGER.warn("response error: status={} message={}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("internal error deleting invoice", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    @GetMapping("/invoices/{id}/photo")
    public ResponseEntity<byte[]> getInvoicePhoto(@PathVariable String id) {
        try {
            String photoPath = invoiceService.getInvoicePhotoPath(id);
            if (photoPath == null) {
                return ResponseEntity.notFound().build();
            }
            Path file = Path.of(photoPath);
            byte[] bytes = Files.readAllBytes(file);
            MediaType mediaType = mediaTypeFor(photoPath);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, mediaType.toString())
                    .body(bytes);
        } catch (Exception e) {
            LOGGER.error("internal error getting invoice photo", e);
            return ResponseEntity.notFound().build();
        }
    }

    private static MediaType mediaTypeFor(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.IMAGE_JPEG;
    }
}
