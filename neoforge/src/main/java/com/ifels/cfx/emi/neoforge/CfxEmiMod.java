package com.ifels.cfx.emi.neoforge;

import com.ifels.cfx.emi.EmiKeys;
import com.ifels.cfx.emi.EmiTicker;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge entry point: registers the pseudo key mappings and pumps the ticker.
 *
 * <p>Both buses are wired by hand rather than with {@code @EventBusSubscriber}: on 21.1 that
 * annotation's {@code bus} argument is deprecated for removal, and the mod constructor already
 * hands us the mod bus.</p>
 */
@Mod(value = CfxEmiMod.MOD_ID, dist = Dist.CLIENT)
public final class CfxEmiMod {

    public static final String MOD_ID = "cfx_compat_emi";

    public CfxEmiMod(ModContainer container) {
        IEventBus modBus = container.getEventBus();
        if (modBus != null) {
            modBus.addListener(RegisterKeyMappingsEvent.class, event -> EmiKeys.registerAll(event::register));
        }
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> EmiTicker.tick());
    }
}
