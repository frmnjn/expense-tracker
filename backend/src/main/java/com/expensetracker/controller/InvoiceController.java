package com.expensetracker.controller;

import com.expensetracker.model.ApiResponse;
import com.expensetracker.service.InvoiceService;
import com.expensetracker.service.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;

@RestController
public class InvoiceController {

    private static final Logger LOGGER = LoggerFactory.getLogger(InvoiceController.class);

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping("/invoices")
    public ResponseEntity<ApiResponse> getInvoices(@RequestParam("date") String date) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(invoiceService.listByDate(date)));
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
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.IMAGE_JPEG;
    }
}
