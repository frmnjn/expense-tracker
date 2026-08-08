package com.expensetracker.service;

import com.expensetracker.data.InvoiceData;
import com.expensetracker.data.InvoiceRepository;
import com.expensetracker.model.InvoicesResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    private InvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        invoiceService = new InvoiceService(invoiceRepository);
        ReflectionTestUtils.setField(invoiceService, "uploadDir", "/tmp");
    }

    @Test
    void listByDate_shouldReturnInvoicesForThatPeriod() {
        when(invoiceRepository.findByPeriod("2026-JUL-AUG"))
                .thenReturn(List.of(new InvoiceData("inv-1", "2026-JUL-AUG", "a.jpg"),
                        new InvoiceData("inv-2", "2026-JUL-AUG", "b.jpg")));
        InvoicesResponse response = invoiceService.listByDate("2026-08-06 14:30");
        assertEquals(2, response.invoices().size());
        assertEquals("inv-1", response.invoices().get(0).id());
        assertEquals("inv-2", response.invoices().get(1).id());
    }

    @Test
    void listByDate_missingDate_shouldReject() {
        ValidationException ex = assertThrows(ValidationException.class, () -> invoiceService.listByDate(""));
        assertEquals("Date is required", ex.getMessage());
    }

    @Test
    void listByDate_invalidFormat_shouldReject() {
        ValidationException ex = assertThrows(ValidationException.class, () -> invoiceService.listByDate("06-08-2026"));
        assertEquals("Date must be in yyyy-MM-dd HH:mm format", ex.getMessage());
    }

    @Test
    void createInvoice_validFile_shouldInsertInvoice() {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("invoice.jpg");
        String id = invoiceService.createInvoice("2026-JUL-AUG", LocalDate.of(2026, 7, 25), file);
        verify(invoiceRepository).insert(eq(id), eq("2026-JUL-AUG"), eq(LocalDate.of(2026, 7, 25)), anyString());
    }

    @Test
    void createInvoice_emptyFile_shouldReject() {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);
        ValidationException ex = assertThrows(ValidationException.class,
                () -> invoiceService.createInvoice("2026-JUL-AUG", LocalDate.of(2026, 7, 25), file));
        assertEquals("Photo is required", ex.getMessage());
    }

    @Test
    void createInvoice_disallowedExtension_shouldReject() {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("invoice.exe");
        ValidationException ex = assertThrows(ValidationException.class,
                () -> invoiceService.createInvoice("2026-JUL-AUG", LocalDate.of(2026, 7, 25), file));
        assertEquals("Only jpg, png, webp, gif are allowed", ex.getMessage());
    }

    @Test
    void requireInvoice_notFound_shouldReject() {
        when(invoiceRepository.findById("missing")).thenReturn(null);
        ValidationException ex = assertThrows(ValidationException.class, () -> invoiceService.requireInvoice("missing"));
        assertEquals("Invoice not found", ex.getMessage());
    }

    @Test
    void requireInvoice_found_shouldReturnInvoice() {
        when(invoiceRepository.findById("inv-1")).thenReturn(new InvoiceData("inv-1", "2026-JUL-AUG", "a.jpg"));
        assertEquals("2026-JUL-AUG", invoiceService.requireInvoice("inv-1").period());
    }
}
