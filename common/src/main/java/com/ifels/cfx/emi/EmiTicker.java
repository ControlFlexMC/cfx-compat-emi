package com.ifels.cfx.emi;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.screen.EmiScreen;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Samples the pseudo keys once per client tick and forwards presses into EMI (spec §8.2).
 *
 * <p>The gates mirror {@code EmiScreenManager.keyPressed} rather than ControlFlex's
 * "controller buttons are commands" stance: if EMI itself would ignore the key, so do we
 * (spec §D7).</p>
 */
public final class EmiTicker {

    private static final Logger LOGGER = LoggerFactory.getLogger("cfx-compat-emi");
    private static final int TEXT_FIELD_DEPTH = 10;

    private static final Map<EmiAction, EdgeDetector> DETECTORS = new EnumMap<>(EmiAction.class);

    private static boolean wasInEmi;

    static {
        for (EmiAction action : EmiAction.values()) {
            DETECTORS.put(action, new EdgeDetector());
        }
    }

    private EmiTicker() {
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;

        if (screen == null || EmiKeys.isEmpty()) {
            drainAll();
            resetAll();
            wasInEmi = false;
            return;
        }
        // EMI 1.0.9 marks every screen it draws into with this interface (HandledScreen gets it
        // through a mixin, RecipeScreen and BoMScreen implement it directly).
        if (!(screen instanceof EmiScreen)) {
            drainAll();
            resetAll();
            wasInEmi = false;
            return;
        }

        boolean entering = !wasInEmi;
        wasInEmi = true;
        if (entering) {
            // A button that opened this screen may still be held: drop its queued click and make
            // the detectors start from the real state instead of a phantom rising edge.
            drainAll();
            for (EmiAction action : EmiAction.values()) {
                KeyMapping mapping = EmiKeys.get(action);
                if (mapping != null) {
                    DETECTORS.get(action).seed(mapping.isDown());
                }
            }
            return;
        }

        boolean disabled = EmiScreenManager.isDisabled();
        boolean focusBlocks = EmiGate.focusBlocks(
                disabled,
                EmiApi.isSearchFocused(),
                !disabled && hasFocusedTextField(screen, TEXT_FIELD_DEPTH));

        List<EmiAction> pending = null;
        for (EmiAction action : EmiAction.values()) {
            KeyMapping mapping = EmiKeys.get(action);
            if (mapping == null) {
                continue;
            }

            boolean down = mapping.isDown();
            boolean clicked = mapping.consumeClick();
            // Update every frame, gated or not: stale state would fire on the frame a gate lifts.
            boolean fired = DETECTORS.get(action).update(down, clicked);
            if (!fired) {
                continue;
            }
            if (disabled && action != EmiAction.TOGGLE_VISIBILITY) {
                continue;
            }
            if (focusBlocks) {
                continue;
            }

            if (pending == null) {
                pending = new ArrayList<>(2);
            }
            pending.add(action);
        }

        if (pending == null) {
            return;
        }

        if (!disabled) {
            try {
                EmiScreenManager.recalculate();
            } catch (Throwable t) {
                LOGGER.warn("[cfx-compat-emi] recalculate failed, skipping this frame", t);
                return;
            }
        }

        for (EmiAction action : pending) {
            try {
                EmiDispatch.fire(action);
            } catch (Throwable t) {
                LOGGER.warn("[cfx-compat-emi] dispatch failed for {}", action.id(), t);
            }
        }
    }

    private static void drainAll() {
        for (EmiAction action : EmiAction.values()) {
            KeyMapping mapping = EmiKeys.get(action);
            if (mapping != null) {
                mapping.consumeClick();
            }
        }
    }

    private static void resetAll() {
        for (EdgeDetector detector : DETECTORS.values()) {
            detector.reset();
        }
    }

    /**
     * Mirrors EMI's private {@code hasFocusedTextField}. Unlike EMI's version this keeps scanning
     * sibling elements instead of returning on the first parent, so a focused box nested beside a
     * non-parent sibling is still found.
     */
    private static boolean hasFocusedTextField(Screen screen, int depth) {
        return scanForFocusedTextField(screen.children(), depth);
    }

    private static boolean scanForFocusedTextField(
            java.util.List<? extends GuiEventListener> children, int depth) {
        if (depth <= 0) {
            return false;
        }
        for (GuiEventListener child : children) {
            if (child instanceof EditBox box && box.isActive() && box.visible) {
                return true;
            }
            if (child instanceof ContainerEventHandler nested
                    && scanForFocusedTextField(nested.children(), depth - 1)) {
                return true;
            }
        }
        return false;
    }
}
