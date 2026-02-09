package com.ecotale.commands;

import com.ecotale.Main;
import com.ecotale.config.EcotaleConfig;
import com.ecotale.economy.EconomyManager;
import com.ecotale.gui.PayGui;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.awt.Color;
import java.util.concurrent.CompletableFuture;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class PayCommand
extends AbstractAsyncCommand {
    private final OptionalArg<PlayerRef> playerArg;
    private final OptionalArg<Double> amountArg;

    public PayCommand() {
        super("pay", "Send money to another player");
        this.setPermissionGroup(GameMode.Adventure);
        this.playerArg = this.withOptionalArg("player", "The player to send money to", (ArgumentType)ArgTypes.PLAYER_REF);
        this.amountArg = this.withOptionalArg("amount", "The amount to send", (ArgumentType)ArgTypes.DOUBLE);
    }

    @NonNullDecl
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        CommandSender sender = ctx.sender();
        if (!(sender instanceof Player)) {
            ctx.sendMessage(Message.raw((String)"This command can only be used by players").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }
        Player player = (Player)sender;
        Ref senderEntity = player.getReference();
        if (senderEntity == null || !senderEntity.isValid()) {
            ctx.sendMessage(Message.raw((String)"Error: Could not get your player data").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }
        Store senderStore = senderEntity.getStore();
        World world = ((EntityStore)senderStore.getExternalData()).getWorld();
        if (world == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> future = new CompletableFuture<Void>();
        world.execute(() -> {
            PlayerRef senderRef = (PlayerRef)senderStore.getComponent(senderEntity, PlayerRef.getComponentType());
            if (senderRef == null) {
                ctx.sendMessage(Message.raw((String)"Error: Could not get player reference").color(Color.RED));
                future.complete(null);
                return;
            }
            PlayerRef targetRef = (PlayerRef)ctx.get(this.playerArg);
            Double amount = (Double)ctx.get(this.amountArg);
            if (targetRef == null && amount == null) {
                player.getPageManager().openCustomPage(senderEntity, senderStore, (CustomUIPage)new PayGui(senderRef));
                future.complete(null);
                return;
            }
            if (targetRef == null) {
                ctx.sendMessage(Message.raw((String)"Usage: /pay <player> <amount> or /pay to open GUI").color(Color.GRAY));
                future.complete(null);
                return;
            }
            if (amount == null || amount <= 0.0) {
                ctx.sendMessage(Message.raw((String)"Amount must be a positive number").color(Color.RED));
                future.complete(null);
                return;
            }
            double minTx = ((EcotaleConfig)Main.CONFIG.get()).getMinimumTransaction();
            if (amount < minTx) {
                ctx.sendMessage(Message.join((Message[])new Message[]{Message.raw((String)"Minimum transaction is ").color(Color.RED), Message.raw((String)((EcotaleConfig)Main.CONFIG.get()).format(minTx)).color(Color.WHITE)}));
                future.complete(null);
                return;
            }
            EconomyManager.TransferResult result = Main.getInstance().getEconomyManager().transfer(senderRef.getUuid(), targetRef.getUuid(), amount, "Player payment");
            switch (result) {
                case SUCCESS: {
                    double fee = amount * ((EcotaleConfig)Main.CONFIG.get()).getTransferFee();
                    player.sendMessage(Message.join((Message[])new Message[]{Message.raw((String)"Payment sent! ").color(Color.GREEN), Message.raw((String)((EcotaleConfig)Main.CONFIG.get()).format(amount)).color(new Color(50, 205, 50)).bold(true), fee > 0.0 ? Message.raw((String)(" (Fee: " + ((EcotaleConfig)Main.CONFIG.get()).format(fee) + ")")).color(Color.GRAY) : Message.raw((String)"")}));
                    break;
                }
                case INSUFFICIENT_FUNDS: {
                    double balance = Main.getInstance().getEconomyManager().getBalance(senderRef.getUuid());
                    player.sendMessage(Message.join((Message[])new Message[]{Message.raw((String)"Insufficient funds. Your balance: ").color(Color.RED), Message.raw((String)((EcotaleConfig)Main.CONFIG.get()).format(balance)).color(Color.WHITE)}));
                    break;
                }
                case SELF_TRANSFER: {
                    player.sendMessage(Message.raw((String)"You cannot send money to yourself").color(Color.RED));
                    break;
                }
                case INVALID_AMOUNT: {
                    player.sendMessage(Message.raw((String)"Invalid amount").color(Color.RED));
                    break;
                }
                case RECIPIENT_MAX_BALANCE: {
                    player.sendMessage(Message.raw((String)"Recipient has reached maximum balance").color(Color.RED));
                }
            }
            future.complete(null);
        });
        return future;
    }
}

