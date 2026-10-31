package com.ifels.cfx.emi;

/**
 * How an action is handed back to EMI (spec §8.4).
 *
 * <p>EMI 1.0.9 — the last build for 1.19.2 — exposes only these two dispatchers. There is no
 * recipe-level dispatcher, so the recipe-kind actions the other branches carry (copy id, and the
 * recipe fallback of favourites) do not exist here.</p>
 */
public enum ActionKind {

    /** {@code EmiScreenManager.stackInteraction} on the hovered stack. */
    STACK,

    /** {@code EmiScreenManager.genericInteraction} (screen-level actions). */
    GENERIC
}
