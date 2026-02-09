package com.ecotale.util;

import com.buuz135.mhud.MultipleHUD;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.Optional;
import java.util.ServiceLoader;
import java.util.logging.Level;

/**
 * Helper for HUD registration with optional MultipleHUD support.
 */
public final class HudHelper {

    private static final String HUD_ID = "ecotale";

    // Optional provider for MultipleHUD support
    private static final Optional<MultipleHUD> MULTIPLE_HUD_PROVIDER;

    static {
        MULTIPLE_HUD_PROVIDER = ServiceLoader.load(com.buuz135.mhud.MultipleHUD.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .findFirst();

        if (MULTIPLE_HUD_PROVIDER.isPresent()) {
            com.ecotale.Main.getInstance().getLogger()
                    .at(Level.INFO).log("Ecotale: MultipleHUD provider found, using compatible mode!");
        } else {
            com.ecotale.Main.getInstance().getLogger()
                    .at(Level.INFO).log("Ecotale: MultipleHUD not detected, using vanilla HUD.");
        }
    }

    private HudHelper() {} // Utility class

    public static void setCustomHud(Player player, PlayerRef playerRef, CustomUIHud hud) {
        if (MULTIPLE_HUD_PROVIDER.isPresent()) {
            try {
                MULTIPLE_HUD_PROVIDER.get().setCustomHud(player, playerRef, HUD_ID, hud);
                return;
            } catch (Exception e) {
                com.ecotale.Main.getInstance().getLogger()
                        .at(Level.WARNING).log("Ecotale: MultipleHUD call failed, falling back to vanilla: " + e.getMessage());
            }
        }

        player.getHudManager().setCustomHud(playerRef, hud);
    }

    public static boolean isMultipleHudAvailable() {
        return MULTIPLE_HUD_PROVIDER.isPresent();
    }
}