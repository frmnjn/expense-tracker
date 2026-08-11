package com.expensetracker.service;

import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Membaca, memvalidasi, resize, dan menyimpan gambar menjadi JPEG terkompresi
 * dengan metadata buatan aplikasi (metadata kamera asli dibuang).
 */
public final class ImageProcessor {

    private static final String APP_SIGNATURE = "ExpenseTracker";
    private static final float JPEG_QUALITY = 0.75f;
    private static final int MAX_IMAGE_SIZE = 1600;

    private static final DateTimeFormatter EXIF_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");
    private static final DateTimeFormatter ISO_TIMESTAMP = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private ImageProcessor() {
    }

    public static void compressAndSave(MultipartFile file, Path target) throws IOException {
        BufferedImage original;
        try (InputStream input = file.getInputStream()) {
            original = ImageIO.read(input);
        }

        if (original == null) {
            throw new ValidationException("Invalid image");
        }

        BufferedImage resized = resizeImage(original, MAX_IMAGE_SIZE);

        try (OutputStream outputStream = Files.newOutputStream(target);
             ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(outputStream)) {

            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (!writers.hasNext()) {
                throw new IllegalStateException("No JPEG writer available");
            }

            ImageWriter writer = writers.next();
            try {
                writer.setOutput(imageOutputStream);

                ImageWriteParam params = writer.getDefaultWriteParam();
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(JPEG_QUALITY);

                IIOMetadata metadata = buildOutputMetadata(writer, resized, original, file, params);
                writer.write(
                        null,
                        new IIOImage(resized, null, metadata),
                        params
                );
            } finally {
                writer.dispose();
            }
        }
    }

    /**
     * Metadata asli (EXIF kamera, orientasi, timestamp original) sengaja dibuang.
     * Diganti EXIF minimal (Orientation=1, Software, DateTimeOriginal kompresi,
     * UserComment, XPComment) plus comment JPEG berisi info kompresi agar asal
     * gambar tetap terlacak. XPComment (0x9C9C, UTF-16LE) dipakai karena kolom
     * "Comments" di Windows Explorer dibaca dari tag itu, bukan COM marker
     * maupun UserComment.
     */
    private static IIOMetadata buildOutputMetadata(ImageWriter writer, BufferedImage resized,
                                                   BufferedImage original, MultipartFile file,
                                                   ImageWriteParam params) throws IOException {
        IIOMetadata metadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(resized), params);
        String formatName = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(formatName);
        IIOMetadataNode markerSequence = (IIOMetadataNode) root.getElementsByTagName("markerSequence").item(0);

        String exifTimestamp = EXIF_TIMESTAMP.format(LocalDateTime.now());
        String commentTimestamp = ISO_TIMESTAMP.format(LocalDateTime.now());
        String commentText = buildCommentText(original, file, commentTimestamp);

        IIOMetadataNode app1 = new IIOMetadataNode("unknown");
        app1.setAttribute("MarkerTag", "225");
        app1.setUserObject(buildExifBytes(exifTimestamp, commentText));
        insertFirst(markerSequence, app1);

        IIOMetadataNode comment = new IIOMetadataNode("com");
        comment.setUserObject(commentText.getBytes(StandardCharsets.UTF_8));
        insertAfter(markerSequence, app1, comment);

        metadata.setFromTree(formatName, root);
        return metadata;
    }

    private static void insertFirst(IIOMetadataNode markerSequence, IIOMetadataNode marker) {
        if (markerSequence.getFirstChild() != null) {
            markerSequence.insertBefore(marker, markerSequence.getFirstChild());
        } else {
            markerSequence.appendChild(marker);
        }
    }

    private static void insertAfter(IIOMetadataNode markerSequence, IIOMetadataNode previous,
                                    IIOMetadataNode marker) {
        if (previous.getNextSibling() != null) {
            markerSequence.insertBefore(marker, previous.getNextSibling());
        } else {
            markerSequence.appendChild(marker);
        }
    }

    private static byte[] buildExifBytes(String dateTime, String comment) {
        byte[] software = asciiBytes(APP_SIGNATURE, true);
        byte[] timestamp = asciiBytes(dateTime, true);
        byte[] userComment = userCommentBytes(comment);
        byte[] xpComment = comment.getBytes(StandardCharsets.UTF_16LE);
        int dataOffset = 8 + 2 + 7 * 12 + 4;
        int size = 6 + dataOffset + software.length + timestamp.length * 3
                + userComment.length + xpComment.length;

        ByteBuffer buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(new byte[]{'E', 'x', 'i', 'f', 0, 0});
        buffer.put((byte) 'I').put((byte) 'I');
        buffer.putShort((short) 42);
        buffer.putInt(8);
        buffer.putShort((short) 7);

        int offset = dataOffset;

        buffer.putShort((short) 0x0112);
        buffer.putShort((short) 3);
        buffer.putInt(1);
        buffer.putInt(1);

        putAsciiTag(buffer, 0x0131, software, offset);
        offset += software.length;

        putAsciiTag(buffer, 0x0132, timestamp, offset);
        offset += timestamp.length;

        putAsciiTag(buffer, 0x9003, timestamp, offset);
        offset += timestamp.length;

        putAsciiTag(buffer, 0x9004, timestamp, offset);
        offset += timestamp.length;

        buffer.putShort((short) 0x9286);
        buffer.putShort((short) 7);
        buffer.putInt(userComment.length);
        buffer.putInt(offset);
        offset += userComment.length;

        buffer.putShort((short) 0x9C9C);
        buffer.putShort((short) 1);
        buffer.putInt(xpComment.length);
        buffer.putInt(offset);

        buffer.putInt(0);

        buffer.put(software);
        buffer.put(timestamp);
        buffer.put(timestamp);
        buffer.put(timestamp);
        buffer.put(userComment);
        buffer.put(xpComment);
        return buffer.array();
    }

    private static void putAsciiTag(ByteBuffer buffer, int tag, byte[] value, int offset) {
        buffer.putShort((short) tag);
        buffer.putShort((short) 2);
        buffer.putInt(value.length);
        buffer.putInt(offset);
    }

    private static byte[] userCommentBytes(String comment) {
        byte[] text = comment.getBytes(StandardCharsets.US_ASCII);
        ByteBuffer buffer = ByteBuffer.allocate(8 + text.length);
        buffer.put(new byte[]{'A', 'S', 'C', 'I', 'I', 0, 0, 0});
        buffer.put(text);
        return buffer.array();
    }

    private static String buildCommentText(BufferedImage original, MultipartFile file, String timestamp) {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        return "Compressed by " + APP_SIGNATURE + "\n"
                + "OriginalSize: " + original.getWidth() + "x" + original.getHeight() + "\n"
                + "OriginalBytes: " + file.getSize() + "\n"
                + "OriginalFile: " + filename + "\n"
                + "Quality: " + JPEG_QUALITY + "\n"
                + "Timestamp: " + timestamp;
    }

    private static byte[] asciiBytes(String value, boolean nulTerminated) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        return nulTerminated ? Arrays.copyOf(bytes, bytes.length + 1) : bytes;
    }

    private static BufferedImage resizeImage(BufferedImage original, int maxSize) {
        int originalWidth = original.getWidth();
        int originalHeight = original.getHeight();

        double scale = Math.min(
                1.0,
                (double) maxSize / Math.max(originalWidth, originalHeight)
        );

        int width = (int) Math.round(originalWidth * scale);
        int height = (int) Math.round(originalHeight * scale);

        if (width == originalWidth && height == originalHeight
                && original.getType() == BufferedImage.TYPE_INT_RGB) {
            return original;
        }

        BufferedImage resized = new BufferedImage(
                width,
                height,
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
            );
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.drawImage(
                    original,
                    0,
                    0,
                    width,
                    height,
                    null
            );
        } finally {
            graphics.dispose();
        }

        return resized;
    }
}
