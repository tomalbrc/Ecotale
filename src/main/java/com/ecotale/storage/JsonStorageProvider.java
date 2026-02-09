package com.ecotale.storage;

import com.ecotale.Main;
import com.ecotale.config.EcotaleConfig;
import com.ecotale.economy.BalanceStorage;
import com.ecotale.economy.PlayerBalance;
import com.ecotale.storage.StorageProvider;
import com.ecotale.util.EcoLogger;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.util.BsonUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

public class JsonStorageProvider
implements StorageProvider {
    private static final Path ECOTALE_PATH = Path.of("mods", "Ecotale_Ecotale");
    private static final Path PLAYERS_PATH = ECOTALE_PATH.resolve("players");
    private static final Path LEGACY_PATH = ECOTALE_PATH.resolve("balances.json");
    private final HytaleLogger logger;
    private final AtomicInteger playerCount = new AtomicInteger(0);

    public JsonStorageProvider() {
        this.logger = HytaleLogger.getLogger().getSubLogger("Ecotale-Storage");
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(PLAYERS_PATH, new FileAttribute[0]);
                if (Files.exists(LEGACY_PATH, new LinkOption[0])) {
                    this.migrateLegacyFormat();
                }
                try (Stream<Path> files = Files.list(PLAYERS_PATH);){
                    int count = (int)files.filter(p -> p.toString().endsWith(".json")).count();
                    this.playerCount.set(count);
                }
                this.logger.at(Level.INFO).log("JsonStorageProvider initialized with %d players", this.playerCount.get());
            }
            catch (IOException e) {
                this.logger.at(Level.SEVERE).log("Failed to initialize storage: %s", (Object)e.getMessage());
                throw new RuntimeException("Storage initialization failed", e);
            }
        });
    }

    @Override
    public CompletableFuture<PlayerBalance> loadPlayer(@Nonnull UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            block7: {
                Path playerFile = this.getPlayerFile(playerUuid);
                if (!Files.exists(playerFile, new LinkOption[0])) {
                    PlayerBalance newBalance = new PlayerBalance(playerUuid);
                    newBalance.setBalance(((EcotaleConfig)Main.CONFIG.get()).getStartingBalance(), "Initial balance");
                    this.playerCount.incrementAndGet();
                    return newBalance;
                }
                try {
                    PlayerBalance balance = (PlayerBalance)RawJsonReader.readSync((Path)playerFile, PlayerBalance.CODEC, (HytaleLogger)this.logger);
                    if (balance != null) {
                        return balance;
                    }
                }
                catch (Exception e) {
                    this.logger.at(Level.WARNING).log("Failed to load %s, trying backup: %s", (Object)playerUuid, (Object)e.getMessage());
                    Path backupFile = this.getBackupFile(playerUuid);
                    if (!Files.exists(backupFile, new LinkOption[0])) break block7;
                    try {
                        PlayerBalance backup = (PlayerBalance)RawJsonReader.readSync((Path)backupFile, PlayerBalance.CODEC, (HytaleLogger)this.logger);
                        if (backup != null) {
                            this.logger.at(Level.INFO).log("Restored %s from backup", (Object)playerUuid);
                            return backup;
                        }
                    }
                    catch (Exception e2) {
                        this.logger.at(Level.SEVERE).log("Backup also failed for %s: %s", (Object)playerUuid, (Object)e2.getMessage());
                    }
                }
            }
            this.logger.at(Level.WARNING).log("Creating new account for %s after load failure", (Object)playerUuid);
            PlayerBalance fallback = new PlayerBalance(playerUuid);
            fallback.setBalance(((EcotaleConfig)Main.CONFIG.get()).getStartingBalance(), "Recovery - initial balance");
            return fallback;
        });
    }

    @Override
    public CompletableFuture<Void> savePlayer(@Nonnull UUID playerUuid, @Nonnull PlayerBalance balance) {
        return CompletableFuture.runAsync(() -> {
            block7: {
                Path playerFile = this.getPlayerFile(playerUuid);
                Path backupFile = this.getBackupFile(playerUuid);
                Path tempFile = this.getTempFile(playerUuid);
                try {
                    BsonUtil.writeSync((Path)tempFile, PlayerBalance.CODEC, balance, (HytaleLogger)this.logger);
                    if (Files.exists(playerFile, new LinkOption[0])) {
                        Files.move(playerFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
                    }
                    Files.move(tempFile, playerFile, StandardCopyOption.ATOMIC_MOVE);
                }
                catch (IOException e) {
                    this.logger.at(Level.SEVERE).log("Failed to save %s: %s", (Object)playerUuid, (Object)e.getMessage());
                    try {
                        Files.deleteIfExists(tempFile);
                    }
                    catch (IOException iOException) {
                        // empty catch block
                    }
                    if (Files.exists(playerFile, new LinkOption[0]) || !Files.exists(backupFile, new LinkOption[0])) break block7;
                    try {
                        Files.copy(backupFile, playerFile, StandardCopyOption.REPLACE_EXISTING);
                        this.logger.at(Level.INFO).log("Restored %s from backup after save failure", (Object)playerUuid);
                    }
                    catch (IOException e2) {
                        this.logger.at(Level.SEVERE).log("Could not restore backup for %s", (Object)playerUuid);
                    }
                }
            }
        });
    }

    @Override
    public CompletableFuture<Void> saveAll(@Nonnull Map<UUID, PlayerBalance> dirtyPlayers) {
        if (dirtyPlayers.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture[] futures = (CompletableFuture[])dirtyPlayers.entrySet().stream().map(entry -> this.savePlayer((UUID)entry.getKey(), (PlayerBalance)entry.getValue())).toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures).thenRun(() -> EcoLogger.debug("Saved %d player balances", dirtyPlayers.size()));
    }

    @Override
    public CompletableFuture<Map<UUID, PlayerBalance>> loadAll() {
        return CompletableFuture.supplyAsync(() -> {
            ConcurrentHashMap allBalances = new ConcurrentHashMap();
            try (Stream<Path> files = Files.list(PLAYERS_PATH);){
                files.filter(p -> p.toString().endsWith(".json") && !p.toString().endsWith(".bak")).forEach(path -> {
                    String filename = path.getFileName().toString();
                    String uuidStr = filename.replace(".json", "");
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        PlayerBalance balance = (PlayerBalance)RawJsonReader.readSync((Path)path, PlayerBalance.CODEC, (HytaleLogger)this.logger);
                        if (balance != null) {
                            allBalances.put(uuid, balance);
                        }
                    }
                    catch (Exception e) {
                        this.logger.at(Level.WARNING).log("Skipping invalid file: %s", (Object)filename);
                    }
                });
            }
            catch (IOException e) {
                this.logger.at(Level.SEVERE).log("Failed to list player files: %s", (Object)e.getMessage());
            }
            return allBalances;
        });
    }

    @Override
    public CompletableFuture<Boolean> playerExists(@Nonnull UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> Files.exists(this.getPlayerFile(playerUuid), new LinkOption[0]));
    }

    @Override
    public CompletableFuture<Void> deletePlayer(@Nonnull UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            try {
                Files.deleteIfExists(this.getPlayerFile(playerUuid));
                Files.deleteIfExists(this.getBackupFile(playerUuid));
                Files.deleteIfExists(this.getTempFile(playerUuid));
                this.playerCount.decrementAndGet();
                this.logger.at(Level.INFO).log("Deleted player data: %s", (Object)playerUuid);
            }
            catch (IOException e) {
                this.logger.at(Level.WARNING).log("Failed to delete player %s: %s", (Object)playerUuid, (Object)e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> this.logger.at(Level.INFO).log("JsonStorageProvider shutdown complete"));
    }

    @Override
    public String getName() {
        return "JSON (per-player files)";
    }

    @Override
    public int getPlayerCount() {
        return this.playerCount.get();
    }

    private Path getPlayerFile(UUID uuid) {
        return PLAYERS_PATH.resolve(uuid.toString() + ".json");
    }

    private Path getBackupFile(UUID uuid) {
        return PLAYERS_PATH.resolve(uuid.toString() + ".json.bak");
    }

    private Path getTempFile(UUID uuid) {
        return PLAYERS_PATH.resolve(uuid.toString() + ".json.tmp");
    }

    private void migrateLegacyFormat() {
        this.logger.at(Level.INFO).log("Migrating from legacy balances.json format...");
        try {
            BalanceStorage legacyStorage = (BalanceStorage)RawJsonReader.readSync((Path)LEGACY_PATH, BalanceStorage.CODEC, (HytaleLogger)this.logger);
            if (legacyStorage != null && legacyStorage.getBalances() != null) {
                int migrated = 0;
                for (PlayerBalance balance : legacyStorage.getBalances()) {
                    Path playerFile = this.getPlayerFile(balance.getPlayerUuid());
                    BsonUtil.writeSync((Path)playerFile, PlayerBalance.CODEC, balance, this.logger);
                    ++migrated;
                }
                Path migratedPath = ECOTALE_PATH.resolve("balances.json.migrated");
                Files.move(LEGACY_PATH, migratedPath, StandardCopyOption.REPLACE_EXISTING);
                this.logger.at(Level.INFO).log("Migration complete: %d players migrated", migrated);
            }
        }
        catch (Exception e) {
            this.logger.at(Level.SEVERE).log("Migration failed: %s", (Object)e.getMessage());
            this.logger.at(Level.WARNING).log("Legacy file preserved, manual migration may be needed");
        }
    }
}

