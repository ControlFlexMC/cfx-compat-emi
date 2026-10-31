package com.ifels.cfx.emi;

import java.util.Locale;

/**
 * The 23 EMI actions this bridge exposes (spec §8.5).
 *
 * <p>{@code bindKey} is the name of the corresponding {@code EmiConfig} field; the per-loader
 * dispatch switches on it. Keeping it as a string is what lets {@code common} stay free of
 * {@code dev.emi.*} types.</p>
 */
public enum EmiAction {

    TOGGLE_VISIBILITY("toggleVisibility", ActionKind.GENERIC),
    FOCUS_SEARCH("focusSearch", ActionKind.GENERIC),
    CLEAR_SEARCH("clearSearch", ActionKind.GENERIC),
    DISPLAY_ALL_RECIPES("displayAllRecipes", ActionKind.API),
    VIEW_RECIPES("viewRecipes", ActionKind.STACK),
    VIEW_USES("viewUses", ActionKind.STACK),
    FAVORITE("favorite", ActionKind.STACK_THEN_RECIPE),
    VIEW_STACK_TREE("viewStackTree", ActionKind.STACK),
    VIEW_TREE("viewTree", ActionKind.GENERIC),
    BACK("back", ActionKind.GENERIC),
    FORWARD("forward", ActionKind.GENERIC),
    CRAFT_ONE("craftOne", ActionKind.STACK),
    CRAFT_ALL("craftAll", ActionKind.STACK),
    CRAFT_ONE_TO_INVENTORY("craftOneToInventory", ActionKind.STACK),
    CRAFT_ALL_TO_INVENTORY("craftAllToInventory", ActionKind.STACK),
    CRAFT_ONE_TO_CURSOR("craftOneToCursor", ActionKind.STACK),
    CHEAT_ONE_TO_INVENTORY("cheatOneToInventory", ActionKind.STACK),
    CHEAT_STACK_TO_INVENTORY("cheatStackToInventory", ActionKind.STACK),
    CHEAT_ONE_TO_CURSOR("cheatOneToCursor", ActionKind.STACK),
    CHEAT_STACK_TO_CURSOR("cheatStackToCursor", ActionKind.STACK),
    HIDE_STACK("hideStack", ActionKind.STACK),
    HIDE_STACK_BY_ID("hideStackById", ActionKind.STACK),
    COPY_ID("copyId", ActionKind.RECIPE);

    public static final String ID_PREFIX = "key.cfx_compat_emi.";
    public static final String CATEGORY = "key.categories.cfx_compat_emi";

    private final String bindKey;
    private final ActionKind kind;
    private final String id;

    EmiAction(String bindKey, ActionKind kind) {
        this.bindKey = bindKey;
        this.kind = kind;
        this.id = ID_PREFIX + toSnakeCase(bindKey);
    }

    /** ControlFlex action id, e.g. {@code key.cfx_compat_emi.view_recipes}. */
    public String id() {
        return id;
    }

    /** Name of the {@code EmiConfig} field this action drives. */
    public String bindKey() {
        return bindKey;
    }

    public ActionKind kind() {
        return kind;
    }

    static String toSnakeCase(String camel) {
        StringBuilder out = new StringBuilder(camel.length() + 4);
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    out.append('_');
                }
                out.append(Character.toLowerCase(c));
            } else {
                out.append(c);
            }
        }
        return out.toString().toLowerCase(Locale.ROOT);
    }
}
