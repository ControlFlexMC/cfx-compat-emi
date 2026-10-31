package com.ifels.cfx.emi.forge;

import com.ifels.cfx.emi.EmiKeys;
import com.ifels.cfx.emi.EmiTicker;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Forge entry point: registers the pseudo key mappings and pumps the ticker.
 */
@Mod(CfxEmiMod.MOD_ID)
public final class CfxEmiMod {

    public static final String MOD_ID = "cfx_compat_emi";

    public CfxEmiMod() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(CfxEmiMod::onClientTick);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            EmiTicker.tick();
        }
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    static final class ClientEvents {

        @SubscribeEvent
        static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            EmiKeys.registerAll(event::register);
        }

        @SubscribeEvent
        static void onClientSetup(FMLClientSetupEvent event) {
            // Key mappings are registered above; nothing else to do at setup time.
        }
    }
}
