package com.ecotale.commands;

import com.ecotale.gui.TopBalanceGui;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.awt.Color;
import java.util.concurrent.CompletableFuture;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class TopBalanceCommand
extends AbstractAsyncCommand {
    public TopBalanceCommand() {
        super("topb", "Open the Top Balance leaderboard");
        this.addAliases(new String[]{"topbalance"});
        this.setPermissionGroup(GameMode.Adventure);
    }

    @NonNullDecl
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        CommandSender commandSender = ctx.sender();
        if (!(commandSender instanceof Player)) {
            ctx.sendMessage(Message.raw((String)"This command can only be used by players").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }
        Player player = (Player)commandSender;
        Ref ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(Message.raw((String)"Error: Could not get your player data").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }
        Store store = ref.getStore();
        World world = ((EntityStore)store.getExternalData()).getWorld();
        if (world == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> future = new CompletableFuture<Void>();
        world.execute(() -> {
            PlayerRef playerRef = (PlayerRef)store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                ctx.sendMessage(Message.raw((String)"Error: Could not get player reference").color(Color.RED));
                future.complete(null);
                return;
            }
            player.getPageManager().openCustomPage(ref, store, (CustomUIPage)new TopBalanceGui(playerRef));
            future.complete(null);
        });
        return future;
    }
}

