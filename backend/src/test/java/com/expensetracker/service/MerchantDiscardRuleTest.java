package com.expensetracker.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerchantDiscardRuleTest {

    private final MerchantDiscardRule rule = new MerchantDiscardRule("superindo");

    @Test
    void matchesCaseInsensitive() {
        assertTrue(rule.shouldDiscard("SUPERINDO CNE"));
        assertTrue(rule.shouldDiscard("Superindo"));
    }

    @Test
    void matchesIgnoringSpacesAndPunctuation() {
        assertTrue(rule.shouldDiscard("SUPER INDO"));
        assertTrue(rule.shouldDiscard("super-indo."));
    }

    @Test
    void doesNotMatchOtherMerchants() {
        assertFalse(rule.shouldDiscard("GUARDIAN 6625 CINERE M"));
        assertFalse(rule.shouldDiscard("KETOPRAK BANG JACKK"));
    }

    @Test
    void emptyConfigNeverDiscards() {
        MerchantDiscardRule empty = new MerchantDiscardRule("");

        assertFalse(empty.shouldDiscard("SUPERINDO CNE"));
    }

    @Test
    void nullMerchantNeverDiscards() {
        assertFalse(rule.shouldDiscard(null));
    }
}
