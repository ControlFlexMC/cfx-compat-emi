package com.ifels.cfx.emi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmiGateTest {

    @Test
    void focusBlocksWhenSearchFocused() {
        assertTrue(EmiGate.focusBlocks(false, true, false));
    }

    @Test
    void focusBlocksWhenAnyTextFieldFocused() {
        assertTrue(EmiGate.focusBlocks(false, false, true));
    }

    @Test
    void focusDoesNotBlockWithoutAFocusedInput() {
        assertFalse(EmiGate.focusBlocks(false, false, false));
    }

    @Test
    void disabledBypassesTheFocusGate() {
        assertFalse(EmiGate.focusBlocks(true, true, true));
    }

    @Test
    void focusedVisibleFieldBlocks() {
        assertTrue(EmiGate.fieldBlocks(true, true));
    }

    @Test
    void visibleButUnfocusedFieldDoesNotBlock() {
        assertFalse(EmiGate.fieldBlocks(false, true));
    }

    @Test
    void focusedHiddenFieldDoesNotBlock() {
        assertFalse(EmiGate.fieldBlocks(true, false));
    }
}
