package com.expensetracker.controller;

import com.expensetracker.model.ApiResponse;
import com.expensetracker.model.BudgetCreateRequest;
import com.expensetracker.model.BudgetUpdateRequest;
import com.expensetracker.model.ExpenseRequest;
import com.expensetracker.model.TopUpRequest;
import com.expensetracker.service.ExpenseService;
import com.expensetracker.service.IdempotencyService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

@RestController
public class ExpenseController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExpenseController.class);

    private final ExpenseService expenseService;
    private final IdempotencyService idempotencyService;

    public ExpenseController(ExpenseService expenseService, IdempotencyService idempotencyService) {
        this.expenseService = expenseService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping("/health")
    public ApiResponse health() {
        return ApiResponse.ok();
    }

    @GetMapping("/options")
    public ResponseEntity<ApiResponse> getOptions() {
        try {
            return ResponseEntity.ok(ApiResponse.ok(expenseService.getOptions()));
        } catch (Exception e) {
            LOGGER.error("internal error getting options", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    @PostMapping("/budgets")
    public ResponseEntity<ApiResponse> createBudget(@RequestBody BudgetCreateRequest request,
                                                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Optional<ApiResponse> cached = idempotencyService.find(idempotencyKey);
        if (cached.isPresent()) {
            return ResponseEntity.ok(cached.get());
        }
        try {
            expenseService.createBudget(request);
            ApiResponse response = ApiResponse.ok();
            idempotencyService.save(idempotencyKey, response);
            return ResponseEntity.ok(response);
        } catch (ValidationException e) {
            LOGGER.warn("response error: status={} message={}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("internal error creating budget", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    @DeleteMapping("/budgets/{name}")
    public ResponseEntity<ApiResponse> deleteBudget(@PathVariable String name) {
        try {
            expenseService.deleteBudget(name);
            return ResponseEntity.ok(ApiResponse.ok());
        } catch (ValidationException e) {
            LOGGER.warn("response error: status={} message={}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("internal error deleting budget", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    @PutMapping("/budgets/{name}")
    public ResponseEntity<ApiResponse> updateBudget(@PathVariable String name,
                                                    @RequestBody BudgetUpdateRequest request) {
        try {
            expenseService.updateBudget(name, request);
            return ResponseEntity.ok(ApiResponse.ok());
        } catch (ValidationException e) {
            LOGGER.warn("response error: status={} message={}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("internal error updating budget", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    @GetMapping("/periods")
    public ResponseEntity<ApiResponse> getPeriods() {
        try {
            return ResponseEntity.ok(ApiResponse.ok(expenseService.getPeriods()));
        } catch (Exception e) {
            LOGGER.error("internal error getting periods", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    @GetMapping("/expenses")
    public ResponseEntity<ApiResponse> getExpenses(@RequestParam("period") String period) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(expenseService.getExpenses(period)));
        } catch (ValidationException e) {
            LOGGER.warn("response error: status={} message={}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("internal error getting expenses", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse> getSummary(@RequestParam("period") String period) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(expenseService.getSummary(period)));
        } catch (ValidationException e) {
            LOGGER.warn("response error: status={} message={}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("internal error getting summary", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    @GetMapping("/trend")
    public ResponseEntity<ApiResponse> getTrend(@RequestParam(value = "months", defaultValue = "3") int months) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(expenseService.getTrend(months)));
        } catch (ValidationException e) {
            LOGGER.warn("response error: status={} message={}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("internal error getting trend", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    @GetMapping("/topups")
    public ResponseEntity<ApiResponse> getTopUps() {
        try {
            return ResponseEntity.ok(ApiResponse.ok(expenseService.getTopUps()));
        } catch (Exception e) {
            LOGGER.error("internal error getting top ups", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    @PostMapping("/topups")
    public ResponseEntity<ApiResponse> createTopUp(@RequestBody TopUpRequest request,
                                                   @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Optional<ApiResponse> cached = idempotencyService.find(idempotencyKey);
        if (cached.isPresent()) {
            return ResponseEntity.ok(cached.get());
        }
        try {
            expenseService.createTopUp(request);
            ApiResponse response = ApiResponse.ok();
            idempotencyService.save(idempotencyKey, response);
            return ResponseEntity.ok(response);
        } catch (ValidationException e) {
            LOGGER.warn("response error: status={} message={}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("internal error creating top up", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    @PostMapping("/expenses")
    public ResponseEntity<ApiResponse> createExpense(@RequestBody ExpenseRequest request,
                                                     @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Optional<ApiResponse> cached = idempotencyService.find(idempotencyKey);
        if (cached.isPresent()) {
            return ResponseEntity.ok(cached.get());
        }
        try {
            String id = expenseService.createExpense(request);
            ApiResponse response = ApiResponse.ok(Map.of("id", id));
            idempotencyService.save(idempotencyKey, response);
            return ResponseEntity.ok(response);
        } catch (ValidationException e) {
            LOGGER.warn("response error: status={} message={}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("internal error creating expense", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    @PostMapping(value = "/expenses/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse> uploadPhoto(@PathVariable String id,
                                                   @RequestParam("file") MultipartFile file,
                                                   @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Optional<ApiResponse> cached = idempotencyService.find(idempotencyKey);
        if (cached.isPresent()) {
            return ResponseEntity.ok(cached.get());
        }
        try {
            expenseService.attachPhoto(id, file);
            ApiResponse response = ApiResponse.ok();
            idempotencyService.save(idempotencyKey, response);
            return ResponseEntity.ok(response);
        } catch (ValidationException e) {
            LOGGER.warn("response error: status={} message={}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("internal error uploading photo", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    @GetMapping("/expenses/{id}/photo")
    public ResponseEntity<byte[]> getPhoto(@PathVariable String id) {
        try {
            String photoPath = expenseService.getPhotoPath(id);
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
            LOGGER.error("internal error getting photo", e);
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

    @PutMapping("/expenses/{id}")
    public ResponseEntity<ApiResponse> updateExpense(@PathVariable String id,
                                                     @RequestBody ExpenseRequest request) {
        try {
            expenseService.updateExpense(id, request);
            return ResponseEntity.ok(ApiResponse.ok());
        } catch (ValidationException e) {
            LOGGER.warn("response error: status={} message={}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("internal error updating expense", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    @DeleteMapping("/expenses/{id}")
    public ResponseEntity<ApiResponse> deleteExpense(@PathVariable String id) {
        try {
            expenseService.deleteExpense(id);
            return ResponseEntity.ok(ApiResponse.ok());
        } catch (ValidationException e) {
            LOGGER.warn("response error: status={} message={}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("internal error deleting expense", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }
}
