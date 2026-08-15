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
import tools.jackson.databind.ObjectMapper;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    private InvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        invoiceService = new InvoiceService(invoiceRepository, new ObjectMapper());
        ReflectionTestUtils.setField(invoiceService, "uploadDir", "/tmp");
    }

    @Test
    void listByDate_shouldReturnInvoicesForThatPeriod() {
        when(invoiceRepository.findByPeriod("2026-JUL-AUG"))
                .thenReturn(List.of(new InvoiceData("inv-1", "2026-JUL-AUG", "a.jpg", "2026-07-25T09:00:00", "SUBMITTED", null),
                        new InvoiceData("inv-2", "2026-JUL-AUG", "b.jpg", "2026-07-26T09:00:00", "SUBMITTED", null)));
        InvoicesResponse response = invoiceService.listByDate("2026-08-06 14:30", false, null);
        assertEquals(2, response.invoices().size());
        assertEquals("inv-1", response.invoices().get(0).id());
        assertEquals("inv-2", response.invoices().get(1).id());
    }

    @Test
    void listByDate_missingDate_shouldReject() {
        ValidationException ex = assertThrows(ValidationException.class, () -> invoiceService.listByDate("", false, null));
        assertEquals("Date is required", ex.getMessage());
    }

    @Test
    void listByDate_invalidFormat_shouldReject() {
        ValidationException ex = assertThrows(ValidationException.class, () -> invoiceService.listByDate("06-08-2026", false, null));
        assertEquals("Date must be in yyyy-MM-dd HH:mm format", ex.getMessage());
    }

    @Test
    void createInvoice_validFile_shouldInsertInvoice() throws Exception {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("invoice.jpg");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(imageBytes(800, 600)));
        String id = invoiceService.createInvoice("2026-JUL-AUG", LocalDate.of(2026, 7, 25), file);
        verify(invoiceRepository).insert(eq(id), eq("2026-JUL-AUG"), eq(LocalDate.of(2026, 7, 25)), eq(id + ".jpg"),
                eq("invoice.jpg"));
        Files.deleteIfExists(Path.of("/tmp", id + ".jpg"));
    }

    @Test
    void createInvoice_invalidImage_shouldReject() throws Exception {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("invoice.jpg");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        ValidationException ex = assertThrows(ValidationException.class,
                () -> invoiceService.createInvoice("2026-JUL-AUG", LocalDate.of(2026, 7, 25), file));
        assertEquals("Invalid image", ex.getMessage());
    }

    @Test
    void createInvoice_largeImage_shouldResizeToMax1600() throws Exception {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("invoice.png");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(imageBytes(3000, 2000)));
        String id = invoiceService.createInvoice("2026-JUL-AUG", LocalDate.of(2026, 7, 25), file);
        try {
            BufferedImage saved = ImageIO.read(Path.of("/tmp", id + ".jpg").toFile());
            assertNotNull(saved);
            assertEquals(1600, saved.getWidth());
            assertEquals(1067, saved.getHeight());
        } finally {
            Files.deleteIfExists(Path.of("/tmp", id + ".jpg"));
        }
    }

    @Test
    void createInvoice_pdf_shouldStoreRawPdf() throws Exception {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("invoice.pdf");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream("%PDF-1.7 fake".getBytes()));
        String id = invoiceService.createInvoice("2026-JUL-AUG", LocalDate.of(2026, 7, 25), file);
        verify(invoiceRepository).insert(eq(id), eq("2026-JUL-AUG"), eq(LocalDate.of(2026, 7, 25)), eq(id + ".pdf"),
                eq("invoice.pdf"));
        assertTrue(Files.readAllBytes(Path.of("/tmp", id + ".pdf")).length > 0);
        Files.deleteIfExists(Path.of("/tmp", id + ".pdf"));
    }

    @Test
    void createInvoiceForAi_shouldSetAnalyzingStatus() throws Exception {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("invoice.jpg");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(imageBytes(200, 100)));
        String id = invoiceService.createInvoiceForAi("2026-JUL-AUG", LocalDate.of(2026, 7, 25), file);
        verify(invoiceRepository).updateStatus(id, "ANALYZING");
        Files.deleteIfExists(Path.of("/tmp", id + ".jpg"));
    }

    @Test
    void listByDate_shouldExposeStatusAndType() {
        when(invoiceRepository.findByPeriod("2026-JUL-AUG"))
                .thenReturn(List.of(new InvoiceData("inv-1", "2026-JUL-AUG", "a.pdf", "2026-07-25T09:00:00", "TO_REVIEW", null)));
        InvoicesResponse response = invoiceService.listByDate("2026-08-06 14:30", false, null);
        assertEquals("TO_REVIEW", response.invoices().get(0).status());
        assertEquals("pdf", response.invoices().get(0).type());
    }

    private static byte[] imageBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void createInvoice_jpeg_shouldStripOriginalMetadataAndWriteOwn() throws Exception {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("invoice.jpg");
        when(file.getSize()).thenReturn(1234L);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(jpegWithApp1Marker()));

        String id = invoiceService.createInvoice("2026-JUL-AUG", LocalDate.of(2026, 7, 25), file);
        try {
            IIOMetadata meta = readMetadata(Path.of("/tmp", id + ".jpg"));

            byte[] marker = findMarkerData(meta, 225);
            assertNotNull(marker, "EXIF baru harus ada");
            String exif = new String(marker, java.nio.charset.StandardCharsets.ISO_8859_1);
            assertTrue(exif.startsWith("Exif\0\0"), "EXIF harus ditulis ulang, bukan disalin dari source");
            assertTrue(exif.contains("ExpenseTracker"), "Software tag harus berisi ExpenseTracker");
            assertTrue(exif.contains("Compressed by ExpenseTracker"),
                    "UserComment (0x9286) harus berisi info kompresi");
            assertTrue(exif.contains("C\0o\0m\0p\0r\0e\0s\0s\0e\0d\0 \0b\0y\0"),
                    "XPComment (0x9C9C) UTF-16LE harus ada agar tampil di kolom Comments Windows Details");
            assertFalse(exif.contains("ASLI-CAMERA-123456"), "metadata asli harus dibuang");

            byte[] comment = findComment(meta);
            assertNotNull(comment, "comment marker harus ada");
            String commentText = new String(comment, java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(commentText.contains("Compressed by ExpenseTracker"));
            assertTrue(commentText.contains("OriginalSize: 100x80"));
            assertTrue(commentText.contains("OriginalBytes: 1234"));
            assertTrue(commentText.contains("OriginalFile: invoice.jpg"));
            assertTrue(commentText.contains("Quality: 0.75"));
        } finally {
            Files.deleteIfExists(Path.of("/tmp", id + ".jpg"));
        }
    }

    private static byte[] jpegWithApp1Marker() throws IOException {
        BufferedImage image = new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            IIOMetadata metadata = writer.getDefaultImageMetadata(new javax.imageio.ImageTypeSpecifier(image), null);
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree("javax_imageio_jpeg_image_1.0");
            IIOMetadataNode markerSequence = (IIOMetadataNode) root.getElementsByTagName("markerSequence").item(0);
            IIOMetadataNode app1 = new IIOMetadataNode("unknown");
            app1.setAttribute("MarkerTag", "225");
            app1.setUserObject("ASLI-CAMERA-123456".getBytes());
            if (markerSequence.getFirstChild() != null) {
                markerSequence.insertBefore(app1, markerSequence.getFirstChild());
            } else {
                markerSequence.appendChild(app1);
            }
            metadata.setFromTree("javax_imageio_jpeg_image_1.0", root);
            writer.write(null, new IIOImage(image, null, metadata), null);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static IIOMetadata readMetadata(Path path) throws IOException {
        try (javax.imageio.stream.ImageInputStream in =
                     ImageIO.createImageInputStream(path.toFile())) {
            var reader = ImageIO.getImageReaders(in).next();
            reader.setInput(in);
            try {
                return reader.getImageMetadata(0);
            } finally {
                reader.dispose();
            }
        }
    }

    private static byte[] findMarkerData(IIOMetadata metadata, int markerTag) {
        return findMarkerData(metadata.getAsTree(metadata.getNativeMetadataFormatName()), markerTag);
    }

    private static byte[] findComment(IIOMetadata metadata) {
        return findComment(metadata.getAsTree(metadata.getNativeMetadataFormatName()));
    }

    private static byte[] findComment(org.w3c.dom.Node node) {
        if ("com".equals(node.getNodeName())) {
            Object userObject = ((IIOMetadataNode) node).getUserObject();
            if (userObject instanceof byte[] bytes) {
                return bytes;
            }
        }
        var children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                byte[] found = findComment(children.item(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static byte[] findMarkerData(org.w3c.dom.Node node, int markerTag) {
        if ("unknown".equals(node.getNodeName())) {
            var markerAttr = node.getAttributes().getNamedItem("MarkerTag");
            if (markerAttr != null && markerAttr.getNodeValue().equals(String.valueOf(markerTag))) {
                Object userObject = ((IIOMetadataNode) node).getUserObject();
                if (userObject instanceof byte[] bytes) {
                    return bytes;
                }
            }
        }
        var children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                byte[] found = findMarkerData(children.item(i), markerTag);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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
        assertEquals("Only jpg, png, and pdf are allowed", ex.getMessage());
    }

    @Test
    void requireInvoice_notFound_shouldReject() {
        when(invoiceRepository.findById("missing")).thenReturn(null);
        ValidationException ex = assertThrows(ValidationException.class, () -> invoiceService.requireInvoice("missing"));
        assertEquals("Invoice not found", ex.getMessage());
    }

    @Test
    void requireInvoice_found_shouldReturnInvoice() {
        when(invoiceRepository.findById("inv-1")).thenReturn(new InvoiceData("inv-1", "2026-JUL-AUG", "a.jpg", "2026-07-25T09:00:00", "SUBMITTED", null));
        assertEquals("2026-JUL-AUG", invoiceService.requireInvoice("inv-1").period());
    }

    @Test
    void deleteIfUnused_stillUsed_shouldDoNothing() {
        when(invoiceRepository.countExpensesUsing("inv-1")).thenReturn(2);
        assertFalse(invoiceService.deleteIfUnused("inv-1"));
        verify(invoiceRepository, never()).delete("inv-1");
    }

    @Test
    void deleteIfUnused_unused_shouldDeleteRowAndFile() throws Exception {
        when(invoiceRepository.countExpensesUsing("inv-1")).thenReturn(0);
        when(invoiceRepository.getPhotoPath("inv-1")).thenReturn("inv-1.jpg");
        // buat file dummy di uploadDir agar Files.deleteIfExists benar-benar menghapus
        Path target = Path.of("/tmp", "inv-1.jpg");
        Files.createDirectories(target.getParent());
        Files.write(target, new byte[]{1, 2, 3});
        ReflectionTestUtils.setField(invoiceService, "uploadDir", "/tmp");

        assertTrue(invoiceService.deleteIfUnused("inv-1"));
        verify(invoiceRepository).delete("inv-1");
        assertFalse(Files.exists(target));
    }

    @Test
    void deleteIfUnused_blankOrNull_shouldDoNothing() {
        assertFalse(invoiceService.deleteIfUnused(null));
        assertFalse(invoiceService.deleteIfUnused("  "));
        verify(invoiceRepository, never()).delete(anyString());
    }
}
