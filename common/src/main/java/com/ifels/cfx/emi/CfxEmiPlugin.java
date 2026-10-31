package com.ifels.cfx.emi;

import com.ifels.controlflex.api.ICompatAssetInstaller;
import com.ifels.controlflex.api.IControlFlexPlugin;

/**
 * ControlFlex SPI entry point for cfx-compat-emi.
 *
 * <p>Registered via
 * {@code META-INF/services/com.ifels.controlflex.api.IControlFlexPlugin}.</p>
 *
 * <p>Installs the bundled compat config, which is what moves the 23 pseudo key mappings into
 * ControlFlex's GUI layer. Without it they would fall back to the MAIN layer and never be
 * dispatched while a screen is open (spec §D4).</p>
 */
public final class CfxEmiPlugin implements IControlFlexPlugin {

    public static final String MOD_ID = "cfx_compat_emi";
    public static final String COMPAT_FILE_NAME = "cfx_compat_emi.json";
    public static final String COMPAT_RESOURCE =
            "/assets/cfx_compat_emi/compat/" + COMPAT_FILE_NAME;

    @Override
    public String getModId() {
        return MOD_ID;
    }

    @Override
    public void onInstallCompatConfigs(ICompatAssetInstaller installer) {
        installer.install(COMPAT_RESOURCE, COMPAT_FILE_NAME);
    }
}
