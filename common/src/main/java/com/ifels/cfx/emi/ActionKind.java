package com.ifels.cfx.emi;

/**
 * How an action is handed back to EMI (spec §8.4).
 */
public enum ActionKind {

    /** {@code EmiScreenManager.stackInteraction} on the hovered stack. */
    STACK,

    /** Stack interaction first, then the recipe-level fallback (used by favourite). */
    STACK_THEN_RECIPE,

    /** {@code EmiScreenManager.recipeInteraction} on the hovered recipe context. */
    RECIPE,

    /** {@code EmiScreenManager.genericInteraction} (screen-level actions). */
    GENERIC,

    /** Direct {@code EmiApi} call. */
    API
}
