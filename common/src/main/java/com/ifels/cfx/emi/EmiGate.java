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
     * <p><b>Focus is required.</b> "Active and visible" is not a focus test: EMI 0.7.3 (1.18.2)
     * registers its search box into the screen's child list, and because {@code EditBox} does not
     * override {@code isActive()}, a visible-but-unfocused box is {@code active && visible} too.
     * Counting that as "a text field has focus" marked every inventory screen as focused and
     * dropped all eighteen actions, silently.</p>
     */
    public static boolean fieldBlocks(boolean focused, boolean visible) {
        return focused && visible;
    }

    /**
     * Whether EMI is currently unavailable, i.e. {@code EmiScreenManager.isDisabled()} on the
     * versions where that method is public.
     *
     * <p>Two inputs, both required: the config switch, and the resource reload. While EMI is
     * reloading its sidebars and search index are empty, so forwarding an action there is the one
     * case that can throw or act on stale state — EMI's own {@code keyPressed} drops everything
     * except "toggle visibility" for the same reason.</p>
     */
    public static boolean emiDisabled(boolean loaded, boolean enabled) {
        return !loaded || !enabled;
    }
}
