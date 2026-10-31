package com.ifels.cfx.emi;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The 23 pseudo key mappings ControlFlex discovers by scanning {@code mc.options.keyMappings}.
 *
 * <p>Names use our own namespace and the default key is deliberately unbound: a pseudo key bound
 * to a real key would also fire on real keystrokes, and could double-fire against EMI's own
 * binds (spec §8.1).</p>
 */
public final class EmiKeys {

    private static final Map<EmiAction, KeyMapping> KEYS = new EnumMap<>(EmiAction.class);

    private EmiKeys() {
    }

    /** Creates every key mapping and hands each one to the loader's registration sink. */
    public static void registerAll(Consumer<KeyMapping> sink) {
        for (EmiAction action : EmiAction.values()) {
            KeyMapping mapping = new KeyMapping(
                    action.id(), InputConstants.UNKNOWN.getValue(), EmiAction.CATEGORY);
            KEYS.put(action, mapping);
            sink.accept(mapping);
        }
    }

    public static KeyMapping get(EmiAction action) {
        return KEYS.get(action);
    }

    public static boolean isEmpty() {
        return KEYS.isEmpty();
    }

    public static Map<EmiAction, KeyMapping> all() {
        return Collections.unmodifiableMap(KEYS);
    }
}
