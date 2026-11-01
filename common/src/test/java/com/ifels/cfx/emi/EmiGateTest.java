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
        // 1.18.2 regression: EMI 0.7.3 adds its search box to the screen's child list, so a
        // visible-but-unfocused box must not count as "a text field has focus".
        assertFalse(EmiGate.fieldBlocks(false, true));
    }

    @Test
    void focusedHiddenFieldDoesNotBlock() {
        assertFalse(EmiGate.fieldBlocks(true, false));
    }

    @Test
    void enabledAndLoadedIsNotDisabled() {
        assertFalse(EmiGate.emiDisabled(true, true));
    }

    @Test
    void disabledByTheConfigSwitch() {
        assertTrue(EmiGate.emiDisabled(true, false));
    }

    @Test
    void disabledWhileReloading() {
        // The gap this guards: with only the config switch checked, the reload window looked
        // enabled and actions were dispatched into an empty EMI.
        assertTrue(EmiGate.emiDisabled(false, true));
    }
}
