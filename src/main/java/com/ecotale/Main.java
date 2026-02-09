package com.ecotale;

import com.buuz135.mhud.MultipleHUD;
import com.ecotale.api.EcotaleAPI;
import com.ecotale.commands.BalanceCommand;
import com.ecotale.commands.EcoAdminCommand;
import com.ecotale.commands.PayCommand;
import com.ecotale.commands.TopBalanceCommand;
import com.ecotale.config.EcotaleConfig;
import com.ecotale.economy.EconomyManager;
import com.ecotale.hud.BalanceHud;
import com.ecotale.lib.vaultunlocked.VaultUnlockedPlugin;
import com.ecotale.security.SecurityLogger;
import com.ecotale.storage.H2StorageProvider;
import com.ecotale.util.PerformanceMonitor;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.common.semver.SemverRange;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.Config;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.time.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class Main
        extends JavaPlugin {
    private static Main instance;
    public static Config<EcotaleConfig> CONFIG;
    private EconomyManager economyManager;
    private ScheduledExecutorService snapshotScheduler;

    public Main(@NonNullDecl JavaPluginInit init) {
        super(init);
        CONFIG = this.withConfig("Ecotale", EcotaleConfig.CODEC);
    }

    protected void setup() {
        super.setup();
        instance = this;
        CONFIG.save();
        this.economyManager = new EconomyManager(this);
        H2StorageProvider h2 = this.economyManager.getH2Storage();
        if (h2 != null) {
            this.snapshotScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Ecotale-TopBalance-Snapshot");
                t.setDaemon(false);
                return t;
            });
            LocalTime snapshotTime = Main.parseSnapshotTime((CONFIG.get()).getTopBalanceSnapshotTime());
            ZoneId zoneId = Main.parseSnapshotZone((CONFIG.get()).getTopBalanceSnapshotTimeZone());
            long initialDelay = Main.computeInitialDelayMillis(snapshotTime, zoneId);
            this.snapshotScheduler.scheduleAtFixedRate(() -> h2.snapshotForDateAsync(LocalDate.now(zoneId)), initialDelay, TimeUnit.DAYS.toMillis(1L), TimeUnit.MILLISECONDS);
        }
        EcotaleAPI.init(this.economyManager, (CONFIG.get()).getRateLimitBurst(), (CONFIG.get()).getRateLimitRefill());
        this.initVaultUnlocked();

        this.getCommandRegistry().registerCommand(new BalanceCommand());
        this.getCommandRegistry().registerCommand(new PayCommand());
        this.getCommandRegistry().registerCommand(new EcoAdminCommand());
        this.getCommandRegistry().registerCommand(new TopBalanceCommand());
        this.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, event -> {
            Player player = event.getHolder().getComponent(Player.getComponentType());
            PlayerRef playerRef = event.getHolder().getComponent(PlayerRef.getComponentType());
            if (player != null && playerRef != null) {
                this.economyManager.ensureAccount(playerRef.getUuid());
                H2StorageProvider h2Storage = this.economyManager.getH2Storage();
                if (h2Storage != null) {
                    h2Storage.updatePlayerName(playerRef.getUuid(), playerRef.getUsername());
                }
                if (CONFIG.get().isEnableHudDisplay()) {
                    BalanceHud hud = new BalanceHud(playerRef);
                    MultipleHUD.getInstance().setCustomHud(player, playerRef, "ecotale", hud);
                }
            }
        });
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            PlayerRef playerRef = event.getPlayerRef();
            EcotaleAPI.resetRateLimit(playerRef.getUuid());
        });
        new PerformanceMonitor();
        this.getLogger().at(Level.INFO).log("Ecotale Economy loaded - HUD balance display active!");
    }

    private void initVaultUnlocked() {
        if (HytaleServer.get().getPluginManager().hasPlugin(PluginIdentifier.fromString((String)"TheNewEconomy:VaultUnlocked"), SemverRange.WILDCARD)) {
            this.getLogger().atInfo().log("VaultUnlocked is installed, enabling VaultUnlocked support.");
            VaultUnlockedPlugin.setup(this.getLogger());
        } else {
            this.getLogger().at(Level.INFO).log("VaultUnlocked is not installed, disabling VaultUnlocked support.");
        }
    }

    protected void shutdown() {
        this.getLogger().at(Level.INFO).log("Ecotale shutting down - saving data...");
        if (SecurityLogger.getInstance() != null) {
            SecurityLogger.getInstance().shutdown();
        }
        if (PerformanceMonitor.getInstance() != null) {
            PerformanceMonitor.getInstance().shutdown();
        }
        if (this.economyManager != null) {
            this.economyManager.shutdown();
        }
        if (this.snapshotScheduler != null) {
            this.snapshotScheduler.shutdown();
        }
        this.getLogger().at(Level.INFO).log("Ecotale shutdown complete!");
    }

    public static Main getInstance() {
        return instance;
    }

    public EconomyManager getEconomyManager() {
        return this.economyManager;
    }

    private static LocalTime parseSnapshotTime(String value) {
        try {
            return LocalTime.parse(value);
        }
        catch (Exception e) {
            return LocalTime.of(3, 0);
        }
    }

    private static ZoneId parseSnapshotZone(String value) {
        try {
            if (value == null || value.isBlank() || value.equalsIgnoreCase("System")) {
                return ZoneId.systemDefault();
            }
            return ZoneId.of(value);
        }
        catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    private static long computeInitialDelayMillis(LocalTime snapshotTime, ZoneId zoneId) {
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        ZonedDateTime next = now.with(snapshotTime);
        if (!next.isAfter(now)) {
            next = next.plusDays(1L);
        }
        return Duration.between(now, next).toMillis();
    }
}

