package com.expensetracker.controller;

import com.expensetracker.data.VoiceNoteData;
import com.expensetracker.model.ApiResponse;
import com.expensetracker.service.IdempotencyService;
import com.expensetracker.service.ValidationException;
import com.expensetracker.service.VoiceNoteAnalysisService;
import com.expensetracker.service.VoiceNoteService;
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
import java.util.Map;
import java.util.Optional;

@RestController
public class VoiceNoteController {

    private static final Logger LOGGER = LoggerFactory.getLogger(VoiceNoteController.class);

    private final VoiceNoteService voiceNoteService;
    private final VoiceNoteAnalysisService voiceNoteAnalysisService;
    private final IdempotencyService idempotencyService;

    public VoiceNoteController(VoiceNoteService voiceNoteService,
                               VoiceNoteAnalysisService voiceNoteAnalysisService,
                               IdempotencyService idempotencyService) {
        this.voiceNoteService = voiceNoteService;
        this.voiceNoteAnalysisService = voiceNoteAnalysisService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping("/voice-notes")
    public ResponseEntity<ApiResponse> getVoiceNotes(@RequestParam(value = "period", required = false) String period) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(voiceNoteService.listByPeriod(period)));
        } catch (ValidationException e) {
            LOGGER.warn("response error: status={} message={}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("internal error getting voice notes", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    @GetMapping("/voice-notes/{id}")
    public ResponseEntity<ApiResponse> getVoiceNote(@PathVariable String id) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(voiceNoteService.getDetail(id)));
        } catch (ValidationException e) {
            LOGGER.warn("response error: status={} message={}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("internal error getting voice note", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    @PostMapping(value = "/voice-notes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse> createVoiceNote(@RequestParam("file") MultipartFile file,
                                                       @RequestParam("date") String date,
                                                       @RequestParam(value = "durationMs", required = false) Integer durationMs,
                                                       @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Optional<ApiResponse> cached = idempotencyService.find(idempotencyKey);
        if (cached.isPresent()) {
            return ResponseEntity.ok(cached.get());
        }
        try {
            String voiceNoteId = voiceNoteService.createForAi(file, date, durationMs);
            voiceNoteAnalysisService.trigger(voiceNoteId);
            ApiResponse response = ApiResponse.ok(Map.of("voiceNoteId", voiceNoteId));
            idempotencyService.save(idempotencyKey, response);
            return ResponseEntity.ok(response);
        } catch (ValidationException e) {
            LOGGER.warn("response error: status={} message={}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("internal error creating voice note", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    @PostMapping("/voice-notes/{id}/retry")
    public ResponseEntity<ApiResponse> retryAnalysis(@PathVariable String id) {
        try {
            voiceNoteService.prepareRetry(id);
            voiceNoteAnalysisService.trigger(id);
            return ResponseEntity.ok(ApiResponse.ok());
        } catch (ValidationException e) {
            LOGGER.warn("response error: status={} message={}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("internal error retrying voice note analysis", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    @DeleteMapping("/voice-notes/{id}")
    public ResponseEntity<ApiResponse> deleteVoiceNote(@PathVariable String id) {
        try {
            voiceNoteService.delete(id);
            return ResponseEntity.ok(ApiResponse.ok());
        } catch (ValidationException e) {
            LOGGER.warn("response error: status={} message={}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("internal error deleting voice note", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error"));
        }
    }

    @GetMapping("/voice-notes/{id}/audio")
    public ResponseEntity<byte[]> getVoiceNoteAudio(@PathVariable String id) {
        try {
            VoiceNoteData note = voiceNoteService.requireVoiceNote(id);
            Path file = voiceNoteService.audioPath(note);
            byte[] bytes = Files.readAllBytes(file);
            MediaType mediaType = note.mimeType() == null || note.mimeType().isBlank()
                    ? MediaType.parseMediaType("audio/webm")
                    : MediaType.parseMediaType(note.mimeType());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, mediaType.toString())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .body(bytes);
        } catch (Exception e) {
            LOGGER.error("internal error getting voice note audio", e);
            return ResponseEntity.notFound().build();
        }
    }
}
