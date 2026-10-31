package com.ifels.cfx.emi;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmiActionTest {

    @Test
    void hasExactlyTwentyTwoActions() {
        assertEquals(22, EmiAction.values().length);
    }

    @Test
    void idsAreUniqueAndPrefixed() {
        Set<String> ids = new HashSet<>();
        for (EmiAction a : EmiAction.values()) {
            assertTrue(a.id().startsWith("key.cfx_compat_emi."), a.name());
            assertTrue(ids.add(a.id()), "duplicate: " + a.id());
        }
    }

    @Test
    void kindAssignmentMatchesSpec() {
        assertEquals(ActionKind.STACK, EmiAction.VIEW_RECIPES.kind());
        assertEquals(ActionKind.STACK_THEN_RECIPE, EmiAction.FAVORITE.kind());
        assertEquals(ActionKind.RECIPE, EmiAction.COPY_ID.kind());
        assertEquals(ActionKind.GENERIC, EmiAction.TOGGLE_VISIBILITY.kind());
    }
}
