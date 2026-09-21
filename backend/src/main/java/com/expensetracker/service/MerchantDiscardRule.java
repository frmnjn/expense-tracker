package com.expensetracker.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Aturan custom untuk otomatis membuang transaksi email berdasarkan keyword
 * merchant. Pencocokan case-insensitive dan toleran spasi/tanda baca:
 * "SUPERINDO CNE", "SUPER INDO", dan "Superindo" sama-sama cocok dengan
 * keyword "superindo".
 */
@Service
public class MerchantDiscardRule {

    private final List<String> keywords;

    public MerchantDiscardRule(@Value("${inbox.discard-merchants:}") String discardMerchants) {
        this.keywords = Arrays.stream(discardMerchants == null ? new String[0] : discardMerchants.split(","))
                .map(MerchantDiscardRule::normalize)
                .filter(k -> !k.isBlank())
                .toList();
    }

    public boolean shouldDiscard(String merchant) {
        if (merchant == null || merchant.isBlank() || keywords.isEmpty()) {
            return false;
        }
        String normalized = normalize(merchant);
        return keywords.stream().anyMatch(normalized::contains);
    }

    static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
