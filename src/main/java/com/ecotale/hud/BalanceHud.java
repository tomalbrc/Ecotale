package com.ecotale.hud;

import com.buuz135.mhud.MultipleHUD;
import com.ecotale.api.EcotaleAPI;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class BalanceHud extends CustomUIHud {
    public BalanceHud(PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    protected void build(@NotNull UICommandBuilder builder) {
        if (!com.ecotale.Main.CONFIG.get().isEnableHudDisplay()) {
            return;
        }

        builder.append("Pages/Ecotale_BalanceHud.ui");

        var config = com.ecotale.Main.CONFIG.get();
        double balance = com.ecotale.Main.getInstance().getEconomyManager().getBalance(getPlayerRef().getUuid());

        String symbol = config.getCurrencySymbol();
        String prefix = com.ecotale.util.TranslationHelper.t(getPlayerRef(), "hud.prefix", config.getHudPrefix());
        String formatted = config.formatShort(balance);

        String amount = formatted.startsWith(symbol)
                ? formatted.substring(symbol.length())
                : formatted;

        builder.set("#CurrencyName.Text", prefix);
        builder.set("#BalanceSymbol.Text", symbol);
        builder.set("#BalanceAmount.Text", amount);
    }

    public static void updatePlayerHud(UUID playerUuid, double newBalance, Double diff) {
        var playerRef = Universe.get().getPlayer(playerUuid);
        if (playerRef == null || playerRef.getWorldUuid() == null)
            return;

        BalanceHud hud = new BalanceHud(Universe.get().getPlayer(playerUuid));

        var world = Universe.get().getWorld(playerRef.getWorldUuid());
        assert world != null;

        var ref = world.getEntityStore().getRefFromUUID(playerUuid);
        if (ref != null && ref.isValid()) {
            var player = world.getEntityStore().getStore().getComponent(ref, Player.getComponentType());
            MultipleHUD.getInstance().setCustomHud(player, playerRef, "ecotale", hud);

            if (diff != null && diff != 0) {
                NotificationUtil.sendNotification(playerRef.getPacketHandler(), (diff >= 0 ? "+" : "-") + " " + EcotaleAPI.format(diff));
            }
        }
    }
}