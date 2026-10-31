package com.ifels.cfx.emi.fabric;

import com.ifels.cfx.emi.EmiKeys;
import com.ifels.cfx.emi.EmiTicker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

/**
 * Fabric entry point: registers the pseudo key mappings and pumps the ticker.
 */
public final class CfxEmiMod implements ClientModInitializer {

    public static final String MOD_ID = "cfx_compat_emi";

    @Override
    public void onInitializeClient() {
        EmiKeys.registerAll(KeyBindingHelper::registerKeyBinding);
        ClientTickEvents.END_CLIENT_TICK.register(client -> EmiTicker.tick());
    }
}
