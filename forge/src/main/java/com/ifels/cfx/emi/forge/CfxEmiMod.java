package com.ifels.cfx.emi.forge;

import net.minecraftforge.fml.common.Mod;

/**
 * Forge entry point for the ControlFlex x EMI bridge.
 *
 * <p>Client-only: the mod registers pseudo key mappings so ControlFlex can list EMI's
 * actions, and forwards the resulting presses back into EMI.</p>
 */
@Mod(CfxEmiMod.MOD_ID)
public final class CfxEmiMod {

    public static final String MOD_ID = "cfx_compat_emi";

    public CfxEmiMod() {
    }
}
