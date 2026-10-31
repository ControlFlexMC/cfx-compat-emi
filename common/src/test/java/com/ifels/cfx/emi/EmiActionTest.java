package com.ifels.cfx.emi;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmiActionTest {

    @Test
    void hasExactlyEighteenActions() {
        assertEquals(18, EmiAction.values().length);
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
        // EMI 1.0.9 has no recipe-level dispatcher, so favourites are stack-only here.
        assertEquals(ActionKind.STACK, EmiAction.FAVORITE.kind());
        assertEquals(ActionKind.GENERIC, EmiAction.TOGGLE_VISIBILITY.kind());
    }
}
