package com.ecotale.systems;

import com.buuz135.mhud.MultipleHUD;
import com.ecotale.hud.BalanceHud;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.Universe;

import java.util.UUID;

/**
 * Tracks active balance HUDs for updates
 * 
 * Uses static methods for easy access from EconomyManager
 */
public class BalanceHudSystem {
    /**
     * Update a player's balance HUD when their balance changes
     */
    public static void updatePlayerHud(UUID playerUuid, double newBalance) {
        var playerRef = Universe.get().getPlayer(playerUuid);
        assert playerRef != null;

        BalanceHud hud = new BalanceHud(Universe.get().getPlayer(playerUuid));

        assert playerRef.getWorldUuid() != null;

        var world = Universe.get().getWorld(playerRef.getWorldUuid());
        assert world != null;

        var ref = world.getEntityStore().getRefFromUUID(playerUuid);
        assert ref != null;

        var player = world.getEntityStore().getStore().getComponent(ref, Player.getComponentType());
        MultipleHUD.getInstance().setCustomHud(player, playerRef, "ecotale", hud);
    }
}
