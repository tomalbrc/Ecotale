package com.ecotale.hud;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

public class BalanceHud extends CustomUIHud {
    public BalanceHud(PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    protected void build(UICommandBuilder builder) {
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
}