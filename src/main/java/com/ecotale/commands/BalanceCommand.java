package com.ecotale.commands;

import com.ecotale.Main;
import com.ecotale.config.EcotaleConfig;
import com.ecotale.economy.PlayerBalance;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.awt.Color;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class BalanceCommand
extends AbstractAsyncCommand {
    public BalanceCommand() {
        super("bal", "Check your balance");
        this.addAliases(new String[]{"balance", "money"});
        this.setPermissionGroup(GameMode.Adventure);
    }

    @NonNullDecl
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        Player player;
        Ref ref;
        CommandSender commandSender = commandContext.sender();
        if (commandSender instanceof Player && (ref = (player = (Player)commandSender).getReference()) != null && ref.isValid()) {
            Store store = ref.getStore();
            return CompletableFuture.runAsync(() -> {
                PlayerRef playerRef = (PlayerRef)store.getComponent(ref, PlayerRef.getComponentType());
                if (playerRef == null) {
                    player.sendMessage(Message.raw((String)"Error: Could not get player data").color(Color.RED));
                    return;
                }
                Main.getInstance().getEconomyManager().ensureAccount(playerRef.getUuid());
                PlayerBalance balance = Main.getInstance().getEconomyManager().getPlayerBalance(playerRef.getUuid());
                if (balance == null) {
                    player.sendMessage(Message.raw((String)"Error: Could not load balance").color(Color.RED));
                    return;
                }
                String formattedBalance = ((EcotaleConfig)Main.CONFIG.get()).format(balance.getBalance());
                String earnedStr = ((EcotaleConfig)Main.CONFIG.get()).formatShort(balance.getTotalEarned());
                String spentStr = ((EcotaleConfig)Main.CONFIG.get()).formatShort(balance.getTotalSpent());
                player.sendMessage(Message.raw((String)"----- Your Balance -----").color(new Color(255, 215, 0)));
                player.sendMessage(Message.join((Message[])new Message[]{Message.raw((String)"  Balance: ").color(Color.GRAY), Message.raw((String)formattedBalance).color(new Color(50, 205, 50)).bold(true)}));
            }, (Executor)player.getWorld());
        }
        return CompletableFuture.completedFuture(null);
    }
}

