package com.ifels.cfx.emi;

/**
 * Pure gate rules, extracted so they can be tested without a client (spec §8.3).
 */
public final class EmiGate {

    private EmiGate() {
    }

    /**
     * Whether a focused input should swallow this press.
     *
     * <p>{@code searchFocused} is separate from {@code textFieldFocused} on purpose: EMI's search
     * widget is rendered and driven by hand and never enters the screen's element tree, so walking
     * the tree alone misses it (review finding C1).</p>
     *
     * <p>While EMI is disabled the whole focus question is skipped: EMI's own branch order lets
     * "toggle visibility" through before it looks at inputs.</p>
     */
    public static boolean focusBlocks(boolean disabled, boolean searchFocused, boolean textFieldFocused) {
        return !disabled && (searchFocused || textFieldFocused);
    }

    /**
     * Whether one text field widget in the screen's element tree should swallow this press.
     *
     * <p><b>Focus is required.</b> "Active and visible" is not a focus test: {@code EditBox} does
     * not override {@code isActive()}, so any visible, enabled box qualifies. EMI 1.1.x keeps its
     * search box out of the element tree, which hides the difference — but 1.18.2 does not, and
     * there the looser test marked every inventory screen as focused and dropped every action
     * silently. Ask for focus directly.</p>
     */
    public static boolean fieldBlocks(boolean focused, boolean visible) {
        return focused && visible;
    }
}
