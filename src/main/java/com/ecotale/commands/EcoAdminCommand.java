package com.ecotale.commands;

import com.ecotale.Main;
import com.ecotale.api.PlayerDBService;
import com.ecotale.config.EcotaleConfig;
import com.ecotale.economy.PlayerBalance;
import com.ecotale.gui.EcoAdminGui;
import com.ecotale.hud.BalanceHud;
import com.ecotale.storage.H2StorageProvider;
import com.ecotale.util.PerformanceMonitor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.CommandUtil;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class EcoAdminCommand
extends AbstractAsyncCommand {
    public EcoAdminCommand() {
        super("eco", "Economy administration commands");
        this.addAliases("economy", "ecoadmin");
        this.setPermissionGroup(null);
        this.addSubCommand(new EcoSetCommand());
        this.addSubCommand(new EcoGiveCommand());
        this.addSubCommand(new EcoTakeCommand());
        this.addSubCommand(new EcoResetCommand());
        this.addSubCommand(new EcoTopCommand());
        this.addSubCommand(new EcoSaveCommand());
        this.addSubCommand(new EcoHudCommand());
        this.addSubCommand(new EcoMetricsCommand());
    }

    @NonNullDecl
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        Player player;
        Ref<EntityStore> ref;
        CommandSender sender = commandContext.sender();
        if (sender instanceof Player && (ref = (player = (Player)sender).getReference()) != null && ref.isValid()) {
            Store<EntityStore> store = ref.getStore();
            World world = (store.getExternalData()).getWorld();
            CompletableFuture<Void> future = new CompletableFuture<Void>();
            world.execute(() -> {
                PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (playerRef != null) {
                    player.getPageManager().openCustomPage(ref, store, new EcoAdminGui(playerRef));
                }
                future.complete(null);
            });
            return future;
        }
        commandContext.sender().sendMessage(Message.raw("=== Ecotale Economy Admin ===").color(new Color(255, 215, 0)));
        commandContext.sender().sendMessage(Message.raw("  /eco set <amount> - Set your balance").color(Color.GRAY));
        commandContext.sender().sendMessage(Message.raw("  /eco give <amount> - Add to balance").color(Color.GRAY));
        commandContext.sender().sendMessage(Message.raw("  /eco take <amount> - Remove from balance").color(Color.GRAY));
        commandContext.sender().sendMessage(Message.raw("  /eco reset - Reset to starting balance").color(Color.GRAY));
        commandContext.sender().sendMessage(Message.raw("  /eco top - Show top balances").color(Color.GRAY));
        commandContext.sender().sendMessage(Message.raw("  /eco metrics - Show performance stats").color(Color.GRAY));
        commandContext.sender().sendMessage(Message.raw("  /eco save - Force save data").color(Color.GRAY));
        return CompletableFuture.completedFuture(null);
    }

    private static void updateHud(UUID playerUuid, double newBalance) {
        BalanceHud.updatePlayerHud(playerUuid, newBalance, null);
    }

    private static class EcoSetCommand
    extends AbstractAsyncCommand {
        public EcoSetCommand() {
            super("set", "Set a player's balance to a specific amount");
            this.setAllowsExtraArguments(true);
        }

        @NonNullDecl
        protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            double amount;
            String input = ctx.getInputString();
            String rawArgsStr = CommandUtil.stripCommandName((String)input);
            String[] rawArgs = rawArgsStr.trim().isEmpty() ? new String[]{} : rawArgsStr.trim().split("\\s+");
            List<String> args = Arrays.asList(rawArgs);
            CommandSender sender = ctx.sender();
            int argOffset = 0;
            if (!args.isEmpty() && (args.getFirst().equalsIgnoreCase("set") || args.getFirst().equalsIgnoreCase("eco"))) {
                ++argOffset;
            }
            if (args.size() > argOffset && args.get(argOffset).equalsIgnoreCase("set")) {
                ++argOffset;
            }
            if (args.size() <= argOffset) {
                ctx.sendMessage(Message.raw((String)"Usage: /eco set <amount> [player]").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
            try {
                amount = Double.parseDouble(args.get(argOffset));
            }
            catch (NumberFormatException e) {
                ctx.sendMessage(Message.raw(("Invalid amount: " + args.get(argOffset))).color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
            if (amount < 0.0) {
                ctx.sendMessage(Message.raw("Amount must be 0 or positive").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
            String targetName = null;
            if (args.size() > argOffset + 1) {
                targetName = args.get(argOffset + 1);
            } else if (sender instanceof Player player) {
                targetName = player.getDisplayName();
            } else {
                ctx.sendMessage(Message.raw("Usage: /eco set <amount> <player> (required for console)").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
            return this.lookupAndExecute(ctx, targetName, amount);
        }

        private CompletableFuture<Void> lookupAndExecute(CommandContext ctx, String targetName, double amount) {
            String finalTargetName;
            if (!targetName.matches("^[a-zA-Z0-9_]{3,16}$")) {
                ctx.sendMessage(Message.raw(("Invalid username format: " + targetName)).color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
            for (PlayerRef online : Universe.get().getPlayers()) {
                if (!online.getUsername().equalsIgnoreCase(targetName)) continue;
                return this.executeOnTarget(ctx, online.getUuid(), targetName, amount);
            }
            H2StorageProvider h2 = Main.getInstance().getEconomyManager().getH2Storage();
            if (h2 != null) {
                finalTargetName = targetName;
                return h2.getPlayerUuidByName(targetName).thenCompose(uuid -> {
                    if (uuid != null) {
                        return this.executeOnTarget(ctx, uuid, finalTargetName, amount);
                    }
                    return PlayerDBService.lookupUuid(finalTargetName).thenCompose(apiUuid -> {
                        if (apiUuid != null) {
                            h2.updatePlayerName(apiUuid, finalTargetName);
                            return this.executeOnTarget(ctx, apiUuid, finalTargetName, amount);
                        }
                        ctx.sendMessage(Message.raw(("Player not found: " + finalTargetName)).color(Color.RED));
                        return CompletableFuture.completedFuture(null);
                    });
                });
            }
            finalTargetName = targetName;
            return PlayerDBService.lookupUuid(targetName).thenCompose(uuid -> {
                if (uuid != null) {
                    return this.executeOnTarget(ctx, (UUID)uuid, finalTargetName, amount);
                }
                ctx.sendMessage(Message.raw((String)("Player not found: " + finalTargetName)).color(Color.RED));
                return CompletableFuture.completedFuture(null);
            });
        }

        private CompletableFuture<Void> executeOnTarget(CommandContext ctx, UUID targetUuid, String targetName, double amount) {
            double oldBalance = Main.getInstance().getEconomyManager().getBalance(targetUuid);
            Main.getInstance().getEconomyManager().setBalance(targetUuid, amount, "Admin set for " + targetName);
            ctx.sendMessage(Message.join((Message[])new Message[]{Message.raw((String)("Set " + targetName + " balance: ")).color(Color.GREEN), Message.raw((String)((EcotaleConfig)Main.CONFIG.get()).format(oldBalance)).color(Color.GRAY), Message.raw((String)" -> ").color(Color.WHITE), Message.raw((String)((EcotaleConfig)Main.CONFIG.get()).format(amount)).color(new Color(50, 205, 50))}));
            EcoAdminCommand.updateHud(targetUuid, amount);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class EcoGiveCommand
    extends AbstractAsyncCommand {
        public EcoGiveCommand() {
            super("give", "Add money to a player's balance");
            this.addAliases("add");
            this.setAllowsExtraArguments(true);
        }

        @NonNullDecl
        protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            double amount;
            String input = ctx.getInputString();
            String rawArgsStr = CommandUtil.stripCommandName(input);
            String[] rawArgs = rawArgsStr.trim().isEmpty() ? new String[]{} : rawArgsStr.trim().split("\\s+");
            List<String> args = Arrays.asList(rawArgs);
            CommandSender sender = ctx.sender();
            int argOffset = 0;
            if (!args.isEmpty() && (args.getFirst().equalsIgnoreCase("give") || args.getFirst().equalsIgnoreCase("add") || args.getFirst().equalsIgnoreCase("eco"))) {
                ++argOffset;
            }
            if (args.size() > argOffset && (args.get(argOffset).equalsIgnoreCase("give") || args.get(argOffset).equalsIgnoreCase("add"))) {
                ++argOffset;
            }
            if (args.size() <= argOffset) {
                ctx.sendMessage(Message.raw("Usage: /eco give <amount> [player]").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
            try {
                amount = Double.parseDouble(args.get(argOffset));
            }
            catch (NumberFormatException e) {
                ctx.sendMessage(Message.raw(("Invalid amount: " + args.get(argOffset))).color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
            if (amount <= 0.0) {
                ctx.sendMessage(Message.raw("Amount must be positive").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
            String targetName = null;
            if (args.size() > argOffset + 1) {
                targetName = args.get(argOffset + 1);
            } else if (sender instanceof Player player) {
                targetName = player.getDisplayName();
            } else {
                ctx.sendMessage(Message.raw("Usage: /eco give <amount> <player> (required for console)").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
            return this.lookupAndExecute(ctx, targetName, amount);
        }

        private CompletableFuture<Void> lookupAndExecute(CommandContext ctx, String targetName, double amount) {
            String finalTargetName;
            if (!targetName.matches("^[a-zA-Z0-9_]{3,16}$")) {
                ctx.sendMessage(Message.raw(("Invalid username format: " + targetName)).color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
            for (PlayerRef online : Universe.get().getPlayers()) {
                if (!online.getUsername().equalsIgnoreCase(targetName)) continue;
                return this.executeOnTarget(ctx, online.getUuid(), targetName, amount);
            }
            H2StorageProvider h2 = Main.getInstance().getEconomyManager().getH2Storage();
            if (h2 != null) {
                finalTargetName = targetName;
                return h2.getPlayerUuidByName(targetName).thenCompose(uuid -> {
                    if (uuid != null) {
                        return this.executeOnTarget(ctx, uuid, finalTargetName, amount);
                    }
                    return PlayerDBService.lookupUuid(finalTargetName).thenCompose(apiUuid -> {
                        if (apiUuid != null) {
                            h2.updatePlayerName(apiUuid, finalTargetName);
                            return this.executeOnTarget(ctx, apiUuid, finalTargetName, amount);
                        }
                        ctx.sendMessage(Message.raw(("Player not found: " + finalTargetName)).color(Color.RED));
                        return CompletableFuture.completedFuture(null);
                    });
                });
            }
            finalTargetName = targetName;
            return PlayerDBService.lookupUuid(targetName).thenCompose(uuid -> {
                if (uuid != null) {
                    return this.executeOnTarget(ctx, (UUID)uuid, finalTargetName, amount);
                }
                ctx.sendMessage(Message.raw(("Player not found: " + finalTargetName)).color(Color.RED));
                return CompletableFuture.completedFuture(null);
            });
        }

        private CompletableFuture<Void> executeOnTarget(CommandContext ctx, UUID targetUuid, String targetName, double amount) {
            Main.getInstance().getEconomyManager().deposit(targetUuid, amount, "Admin give to " + targetName);
            double newBalance = Main.getInstance().getEconomyManager().getBalance(targetUuid);
            ctx.sendMessage(Message.join(Message.raw("Added ").color(Color.GREEN), Message.raw((String)("+" + ((EcotaleConfig)Main.CONFIG.get()).format(amount))).color(new Color(50, 205, 50)), Message.raw((String)(" to " + targetName)).color(Color.WHITE), Message.raw((String)" | New balance: ").color(Color.GRAY), Message.raw((String)((EcotaleConfig)Main.CONFIG.get()).format(newBalance)).color(Color.WHITE)));
            EcoAdminCommand.updateHud(targetUuid, newBalance);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class EcoTakeCommand
    extends AbstractAsyncCommand {
        public EcoTakeCommand() {
            super("take", "Remove money from a player's balance");
            this.addAliases(new String[]{"remove"});
            this.setAllowsExtraArguments(true);
        }

        @NonNullDecl
        protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            double amount;
            String input = ctx.getInputString();
            String rawArgsStr = CommandUtil.stripCommandName(input);
            String[] rawArgs = rawArgsStr.trim().isEmpty() ? new String[]{} : rawArgsStr.trim().split("\\s+");
            List<String> args = Arrays.asList(rawArgs);
            CommandSender sender = ctx.sender();
            int argOffset = 0;
            if (!args.isEmpty() && (args.getFirst().equalsIgnoreCase("take") || args.getFirst().equalsIgnoreCase("remove") || args.getFirst().equalsIgnoreCase("eco"))) {
                ++argOffset;
            }
            if (args.size() > argOffset && (args.get(argOffset).equalsIgnoreCase("take") || args.get(argOffset).equalsIgnoreCase("remove"))) {
                ++argOffset;
            }
            if (args.size() <= argOffset) {
                ctx.sendMessage(Message.raw("Usage: /eco take <amount> [player]").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
            try {
                amount = Double.parseDouble(args.get(argOffset));
            }
            catch (NumberFormatException e) {
                ctx.sendMessage(Message.raw(("Invalid amount: " + args.get(argOffset))).color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
            if (amount <= 0.0) {
                ctx.sendMessage(Message.raw("Amount must be positive").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
            String targetName = null;
            if (args.size() > argOffset + 1) {
                targetName = args.get(argOffset + 1);
            } else if (sender instanceof Player player) {
                targetName = player.getDisplayName();
            } else {
                ctx.sendMessage(Message.raw("Usage: /eco take <amount> <player> (required for console)").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
            return this.lookupAndExecute(ctx, targetName, amount);
        }

        private CompletableFuture<Void> lookupAndExecute(CommandContext ctx, String targetName, double amount) {
            String finalTargetName;
            if (!targetName.matches("^[a-zA-Z0-9_]{3,16}$")) {
                ctx.sendMessage(Message.raw((String)("Invalid username format: " + targetName)).color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
            for (PlayerRef online : Universe.get().getPlayers()) {
                if (!online.getUsername().equalsIgnoreCase(targetName)) continue;
                return this.executeOnTarget(ctx, online.getUuid(), targetName, amount);
            }
            H2StorageProvider h2 = Main.getInstance().getEconomyManager().getH2Storage();
            if (h2 != null) {
                finalTargetName = targetName;
                return h2.getPlayerUuidByName(targetName).thenCompose(uuid -> {
                    if (uuid != null) {
                        return this.executeOnTarget(ctx, uuid, finalTargetName, amount);
                    }
                    return PlayerDBService.lookupUuid(finalTargetName).thenCompose(apiUuid -> {
                        if (apiUuid != null) {
                            h2.updatePlayerName(apiUuid, finalTargetName);
                            return this.executeOnTarget(ctx, apiUuid, finalTargetName, amount);
                        }
                        ctx.sendMessage(Message.raw(("Player not found: " + finalTargetName)).color(Color.RED));
                        return CompletableFuture.completedFuture(null);
                    });
                });
            }
            finalTargetName = targetName;
            return PlayerDBService.lookupUuid(targetName).thenCompose(uuid -> {
                if (uuid != null) {
                    return this.executeOnTarget(ctx, uuid, finalTargetName, amount);
                }
                ctx.sendMessage(Message.raw(("Player not found: " + finalTargetName)).color(Color.RED));
                return CompletableFuture.completedFuture(null);
            });
        }

        private CompletableFuture<Void> executeOnTarget(CommandContext ctx, UUID targetUuid, String targetName, double amount) {
            boolean success = Main.getInstance().getEconomyManager().withdraw(targetUuid, amount, "Admin take from " + targetName);
            double newBalance = Main.getInstance().getEconomyManager().getBalance(targetUuid);
            if (success) {
                ctx.sendMessage(Message.join(Message.raw("Removed ").color(Color.YELLOW), Message.raw((String)("-" + ((EcotaleConfig)Main.CONFIG.get()).format(amount))).color(new Color(255, 99, 71)), Message.raw((String)(" from " + targetName)).color(Color.WHITE), Message.raw((String)" | New balance: ").color(Color.GRAY), Message.raw((String)((EcotaleConfig)Main.CONFIG.get()).format(newBalance)).color(Color.WHITE)));
                EcoAdminCommand.updateHud(targetUuid, newBalance);
            } else {
                ctx.sendMessage(Message.raw((targetName + " has insufficient funds")).color(Color.RED));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class EcoResetCommand
    extends AbstractAsyncCommand {
        public EcoResetCommand() {
            super("reset", "Reset balance to starting amount");
        }

        @NonNullDecl
        protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            CommandSender sender = ctx.sender();
            if (!(sender instanceof Player player)) {
                ctx.sendMessage(Message.raw("This command can only be used by players").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }

            Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) {
                return CompletableFuture.completedFuture(null);
            }
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();
            return CompletableFuture.runAsync(() -> {
                PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (playerRef == null) {
                    return;
                }
                double startingBalance = (Main.CONFIG.get()).getStartingBalance();
                Main.getInstance().getEconomyManager().setBalance(playerRef.getUuid(), startingBalance, "Admin reset");
                EcoAdminCommand.updateHud(playerRef.getUuid(), startingBalance);
                player.sendMessage(Message.join(Message.raw("Balance reset to ").color(Color.GREEN), Message.raw(Main.CONFIG.get().format(startingBalance)).color(new Color(50, 205, 50))));
            }, world);
        }
    }

    private static class EcoTopCommand
    extends AbstractAsyncCommand {
        public EcoTopCommand() {
            super("top", "Show top balances");
        }

        @NonNullDecl
        protected CompletableFuture<Void> executeAsync(@NotNull CommandContext ctx) {
            Map<UUID, PlayerBalance> balances = Main.getInstance().getEconomyManager().getAllBalances();
            if (balances.isEmpty()) {
                ctx.sendMessage(Message.raw("No player balances found").color(Color.GRAY));
                return CompletableFuture.completedFuture(null);
            }
            ctx.sendMessage(Message.raw("=== Top Balances ===").color(new Color(255, 215, 0)));
            H2StorageProvider h2Storage = Main.getInstance().getEconomyManager().getH2Storage();
            List<PlayerBalance> top10 = balances.values().stream().sorted(Comparator.comparingDouble(PlayerBalance::getBalance).reversed()).limit(10L).toList();
            List<CompletableFuture<String>> nameFutures = top10.stream().map(balance -> {
                if (h2Storage != null) {
                    return h2Storage.getPlayerNameAsync(balance.getPlayerUuid()).thenApply(name -> name != null ? name : balance.getPlayerUuid().toString().substring(0, 8) + "...");
                }
                return CompletableFuture.completedFuture(balance.getPlayerUuid().toString().substring(0, 8) + "...");
            }).toList();
            return CompletableFuture.allOf(nameFutures.toArray(new CompletableFuture[0])).thenAccept(v -> {
                for (int i = 0; i < top10.size(); ++i) {
                    PlayerBalance balance = top10.get(i);
                    String displayName = nameFutures.get(i).join();
                    String formatted = Main.CONFIG.get().format(balance.getBalance());
                    ctx.sendMessage(Message.join(Message.raw(("#" + (i + 1) + " ")).color(Color.GRAY), Message.raw(displayName).color(Color.WHITE), Message.raw(" - ").color(Color.GRAY), Message.raw((String)formatted).color(new Color(50, 205, 50))));
                }
            });
        }
    }

    private static class EcoSaveCommand
    extends AbstractAsyncCommand {
        public EcoSaveCommand() {
            super("save", "Force save all data");
        }

        @NonNullDecl
        protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            Main.getInstance().getEconomyManager().forceSave();
            ctx.sendMessage(Message.raw("\u2713 Economy data saved successfully").color(Color.GREEN));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class EcoHudCommand
    extends AbstractAsyncCommand {
        public EcoHudCommand() {
            super("hud", "Toggle HUD display on/off");
        }

        @NonNullDecl
        protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            EcotaleConfig config = (EcotaleConfig)Main.CONFIG.get();
            boolean newValue = !config.isEnableHudDisplay();
            config.setEnableHudDisplay(newValue);
            String status = newValue ? "\u00a7aEnabled" : "\u00a7cDisabled";
            ctx.sendMessage(Message.raw(("HUD Display: " + status)).color(newValue ? Color.GREEN : Color.RED));
            ctx.sendMessage(Message.raw("Use /eco save to persist this change").color(Color.GRAY));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class EcoMetricsCommand
    extends AbstractAsyncCommand {
        public EcoMetricsCommand() {
            super("metrics", "Show performance and scaling metrics");
            this.addAliases(new String[]{"stats", "perf"});
        }

        @NonNullDecl
        protected CompletableFuture<Void> executeAsync(@NotNull CommandContext ctx) {
            PerformanceMonitor monitor = PerformanceMonitor.getInstance();
            if (monitor != null) {
                Color gold = new Color(255, 215, 0);
                Color white = Color.WHITE;
                Color green = new Color(50, 205, 50);
                ctx.sendMessage(Message.raw("--- Ecotale Economy Metrics ---").color(gold));
                ctx.sendMessage(Message.join(Message.raw("Cached Balances: ").color(white), Message.raw((monitor.getCachedPlayers() + " / 1000")).color(green)));
                ctx.sendMessage(Message.raw("---------------------------------").color(gold));
                ctx.sendMessage(Message.raw("System metrics moved to /guard metrics").color(Color.GRAY));
            } else {
                ctx.sendMessage(Message.raw("Performance monitor is not active.").color(Color.RED));
            }
            return CompletableFuture.completedFuture(null);
        }
    }
}

