package com.ifels.cfx.emi;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.input.EmiBind;
import dev.emi.emi.screen.EmiScreenManager;

/**
 * Executes one EMI action by calling EMI's own interaction dispatchers with a predicate that
 * matches exactly one {@link EmiBind} (spec §D3).
 *
 * <p>This is why the bridge needs no key injection and no mixins: the behaviour branches
 * (crafting, hiding, cheat-give, recipe navigation) all live behind these public static
 * functions.</p>
 */
public final class EmiDispatch {

    private EmiDispatch() {
    }

    public static boolean fire(EmiAction action) {
        EmiStackInteraction hovered = EmiApi.getHoveredStack(true);
        EmiBind bind = bind(action);
        return switch (action.kind()) {
            case STACK -> EmiScreenManager.stackInteraction(hovered, b -> b == bind);
            case GENERIC -> EmiScreenManager.genericInteraction(b -> b == bind);
        };
    }

    /** Exhaustive on purpose: adding an {@link EmiAction} without a bind must not compile. */
    private static EmiBind bind(EmiAction action) {
        return switch (action) {
            case TOGGLE_VISIBILITY -> EmiConfig.toggleVisibility;
            case FOCUS_SEARCH -> EmiConfig.focusSearch;
            case CLEAR_SEARCH -> EmiConfig.clearSearch;
            case VIEW_RECIPES -> EmiConfig.viewRecipes;
            case VIEW_USES -> EmiConfig.viewUses;
            case FAVORITE -> EmiConfig.favorite;
            case VIEW_STACK_TREE -> EmiConfig.viewStackTree;
            case VIEW_TREE -> EmiConfig.viewTree;
            case BACK -> EmiConfig.back;
            case CRAFT_ONE -> EmiConfig.craftOne;
            case CRAFT_ALL -> EmiConfig.craftAll;
            case CRAFT_ONE_TO_INVENTORY -> EmiConfig.craftOneToInventory;
            case CRAFT_ALL_TO_INVENTORY -> EmiConfig.craftAllToInventory;
            case CRAFT_ONE_TO_CURSOR -> EmiConfig.craftOneToCursor;
            case CHEAT_ONE_TO_INVENTORY -> EmiConfig.cheatOneToInventory;
            case CHEAT_STACK_TO_INVENTORY -> EmiConfig.cheatStackToInventory;
            case CHEAT_ONE_TO_CURSOR -> EmiConfig.cheatOneToCursor;
            case CHEAT_STACK_TO_CURSOR -> EmiConfig.cheatStackToCursor;
            case HIDE_STACK -> EmiConfig.hideStack;
            case HIDE_STACK_BY_ID -> EmiConfig.hideStackById;
        };
    }
}
