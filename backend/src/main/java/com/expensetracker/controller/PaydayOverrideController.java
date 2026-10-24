package com.expensetracker.controller;

import com.expensetracker.model.ApiResponse;
import com.expensetracker.model.PaydayOverrideRequest;
import com.expensetracker.service.PaydayOverrideService;
import com.expensetracker.service.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@RestController
public class PaydayOverrideController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaydayOverrideController.class);

    private final PaydayOverrideService paydayOverrideService;

    public PaydayOverrideController(PaydayOverrideService paydayOverrideService) {
        this.paydayOverrideService = paydayOverrideService;
    }

    @PutMapping("/payday-overrides/{period}")
    public ResponseEntity<ApiResponse> save(@PathVariable String period,
                                            @RequestBody PaydayOverrideRequest request) {
        try {
            if (request == null || request.date() == null || request.date().isBlank()) {
                throw new ValidationException("Date is required");
            }
            LocalDate date = LocalDate.parse(request.date());
            paydayOverrideService.save(period, date);
            return ResponseEntity.ok(ApiResponse.ok());
        } catch (DateTimeParseException e) {
            LOGGER.warn("response error: status={} message={}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Date must be in yyyy-MM-dd format"));
        } catch (ValidationException e) {
            LOGGER.warn("response error: status={} message={}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("internal error saving payday override", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    @DeleteMapping("/payday-overrides/{period}")
    public ResponseEntity<ApiResponse> delete(@PathVariable String period) {
        try {
            paydayOverrideService.delete(period);
            return ResponseEntity.ok(ApiResponse.ok());
        } catch (ValidationException e) {
            LOGGER.warn("response error: status={} message={}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("internal error deleting payday override", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }
}
