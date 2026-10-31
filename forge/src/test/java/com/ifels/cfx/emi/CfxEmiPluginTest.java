package com.ifels.cfx.emi;

import com.ifels.controlflex.api.ICompatAssetInstaller;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CfxEmiPluginTest {

    @Test
    void installsCompatJson() {
        List<String[]> calls = new ArrayList<>();
        ICompatAssetInstaller installer = (resource, name) -> {
            calls.add(new String[]{resource, name});
            return true;
        };

        new CfxEmiPlugin().onInstallCompatConfigs(installer);

        assertEquals(1, calls.size(), "exactly one asset installed");
        assertEquals("/assets/cfx_compat_emi/compat/cfx_compat_emi.json", calls.get(0)[0]);
        assertEquals("cfx_compat_emi.json", calls.get(0)[1]);
    }

    @Test
    void compatJsonListsEveryAction() throws Exception {
        String json;
        try (var in = CfxEmiPlugin.class.getResourceAsStream(CfxEmiPlugin.COMPAT_RESOURCE)) {
            assertTrue(in != null, "missing resource " + CfxEmiPlugin.COMPAT_RESOURCE);
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        for (EmiAction action : EmiAction.values()) {
            assertTrue(json.contains("\"" + action.id() + "\""), "missing " + action.id());
        }
        assertTrue(json.contains("\"channels\": [\"keyMapping\"]"), "channels must pin keyMapping");
        assertTrue(json.contains("\"mod_id\": \"emi\""),
                "the config must be attributed to EMI so the actions live in EMI's group");
    }
}
