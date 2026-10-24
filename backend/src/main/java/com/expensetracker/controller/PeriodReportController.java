package com.expensetracker.controller;

import com.expensetracker.model.ApiResponse;
import com.expensetracker.service.PeriodReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@RestController
public class PeriodReportController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeriodReportController.class);

    private final PeriodReportService periodReportService;

    public PeriodReportController(PeriodReportService periodReportService) {
        this.periodReportService = periodReportService;
    }

    @GetMapping("/reports")
    public ResponseEntity<ApiResponse> list() {
        try {
            return ResponseEntity.ok(ApiResponse.ok(periodReportService.getReports()));
        } catch (Exception e) {
            LOGGER.error("internal error listing period reports", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    @GetMapping("/reports/{period}")
    public ResponseEntity<?> download(@PathVariable String period,
                                      @RequestParam(value = "inline", defaultValue = "false") boolean inline) {
        Optional<Path> report = periodReportService.findReport(period);
        if (report.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Report not found"));
        }
        try {
            byte[] body = Files.readAllBytes(report.get());
            String fileName = report.get().getFileName().toString();
            ContentDisposition disposition = inline
                    ? ContentDisposition.inline().filename(fileName).build()
                    : ContentDisposition.attachment().filename(fileName).build();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(body);
        } catch (IOException e) {
            LOGGER.error("failed to read period report {}", period, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to read report"));
        }
    }
}
