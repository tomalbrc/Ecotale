package com.ecotale.economy;

import com.ecotale.Main;
import com.ecotale.api.events.BalanceChangeEvent;
import com.ecotale.api.events.EcotaleEvents;
import com.ecotale.config.EcotaleConfig;
import com.ecotale.hud.BalanceHud;
import com.ecotale.storage.H2StorageProvider;
import com.ecotale.storage.JsonStorageProvider;
import com.ecotale.storage.MySQLStorageProvider;
import com.ecotale.storage.StorageProvider;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class EconomyManager {
    private final ConcurrentHashMap<UUID, PlayerBalance> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ReentrantLock> playerLocks = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    private final StorageProvider storage;
    private final TransactionLogger transactionLogger = TransactionLogger.getInstance();
    private volatile List<Map.Entry<UUID, PlayerBalance>> cachedLeaderboard;
    private volatile long lastLeaderboardRebuild = 0L;
    private static final long LEADERBOARD_CACHE_MS = 2000L;
    private static final int MAX_LEADERBOARD_CACHE_SIZE = 100;
    private static final int UUID_PREVIEW_LENGTH = 8;
    private volatile long lastLockCleanup = System.currentTimeMillis();
    private static final long LOCK_CLEANUP_INTERVAL_MS = 1800000L;
    private volatile boolean running = true;
    private final Thread saveThread;
    private final HytaleLogger logger = HytaleLogger.getLogger().getSubLogger("Ecotale");

    public EconomyManager(@Nonnull Object plugin) {
        String providerType;
        switch (providerType = ((EcotaleConfig) Main.CONFIG.get()).getStorageProvider().toLowerCase()) {
            case "mysql": {
                MySQLStorageProvider mysql = new MySQLStorageProvider();
                this.storage = mysql;
                this.transactionLogger.setMysqlStorage(mysql);
                this.logger.at(Level.INFO).log("Using MySQL storage provider (shared database)");
                break;
            }
            case "json": {
                this.storage = new JsonStorageProvider();
                this.logger.at(Level.INFO).log("Using JSON storage provider");
                break;
            }
            default: {
                H2StorageProvider h2 = new H2StorageProvider();
                this.storage = h2;
                this.transactionLogger.setH2Storage(h2);
                this.logger.at(Level.INFO).log("Using H2 storage provider");
            }
        }
        this.storage.initialize().join();
        this.bulkPreload();
        this.saveThread = new Thread(this::autoSaveLoop, "Ecotale-AutoSave");
        this.saveThread.setDaemon(true);
        this.saveThread.start();
        this.logger.at(Level.INFO).log("EconomyManager initialized with %s (%d players preloaded)", (Object) this.storage.getName(), this.cache.size());
    }

    private ReentrantLock getLock(UUID playerUuid) {
        return this.playerLocks.computeIfAbsent(playerUuid, k -> new ReentrantLock());
    }

    public void ensureAccount(@Nonnull UUID playerUuid) {
        this.cache.computeIfAbsent(playerUuid, uuid -> {
            PlayerBalance balance = this.storage.loadPlayer((UUID) uuid).join();
            this.dirtyPlayers.add((UUID) uuid);
            return balance;
        });
    }

    private PlayerBalance getOrLoadAccount(@Nonnull UUID playerUuid) {
        return this.cache.computeIfAbsent(playerUuid, uuid -> this.storage.loadPlayer((UUID) uuid).join());
    }

    public double getBalance(@Nonnull UUID playerUuid) {
        PlayerBalance balance = this.cache.get(playerUuid);
        return balance != null ? balance.getBalance() : 0.0;
    }

    public PlayerBalance getPlayerBalance(@Nonnull UUID playerUuid) {
        return this.cache.get(playerUuid);
    }

    public boolean hasBalance(@Nonnull UUID playerUuid, double amount) {
        return this.getBalance(playerUuid) >= amount;
    }

    public boolean deposit(@Nonnull UUID playerUuid, double amount, String reason) {
        ReentrantLock lock = this.getLock(playerUuid);
        lock.lock();
        try {
            double newBalance;
            PlayerBalance balance = this.getOrLoadAccount(playerUuid);
            if (balance == null) {
                return false;
            }
            double oldBalance = balance.getBalance();
            BalanceChangeEvent event = EcotaleEvents.fire(new BalanceChangeEvent(playerUuid, oldBalance, newBalance = oldBalance + amount, BalanceChangeEvent.Cause.DEPOSIT, reason != null ? reason : "Deposit"));
            if (event.isCancelled()) {
                return false;
            }
            if (balance.deposit(amount, reason)) {
                this.dirtyPlayers.add(playerUuid);
                BalanceHud.updatePlayerHud(playerUuid, balance.getBalance(), amount);
                if (reason != null && !reason.startsWith("Transfer")) {
                    TransactionType type = reason.startsWith("Admin") ? TransactionType.GIVE : TransactionType.EARN;
                    this.transactionLogger.logAction(type, playerUuid, this.resolvePlayerName(playerUuid), amount);
                }
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }


    public boolean withdraw(@Nonnull UUID playerUuid, double amount, String reason) {
        ReentrantLock lock = this.getLock(playerUuid);
        lock.lock();
        try {
            double newBalance;
            PlayerBalance balance = this.cache.get(playerUuid);
            if (balance == null) {
                return false;
            }
            double oldBalance = balance.getBalance();
            BalanceChangeEvent event = EcotaleEvents.fire(new BalanceChangeEvent(playerUuid, oldBalance, newBalance = oldBalance - amount, BalanceChangeEvent.Cause.WITHDRAW, reason != null ? reason : "Withdraw"));
            if (event.isCancelled()) {
                return false;
            }
            if (balance.withdraw(amount, reason)) {
                this.dirtyPlayers.add(playerUuid);
                BalanceHud.updatePlayerHud(playerUuid, balance.getBalance(), -amount);
                if (reason != null && !reason.startsWith("Transfer")) {
                    TransactionType type = reason.startsWith("Admin") ? TransactionType.TAKE : TransactionType.SPEND;
                    this.transactionLogger.logAction(type, playerUuid, this.resolvePlayerName(playerUuid), amount);
                }
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public void setBalance(@Nonnull UUID playerUuid, double amount, String reason) {
        ReentrantLock lock = this.getLock(playerUuid);
        lock.lock();
        try {
            PlayerBalance balance = this.getOrLoadAccount(playerUuid);
            if (balance != null) {
                double oldBalance = balance.getBalance();
                BalanceChangeEvent event = EcotaleEvents.fire(new BalanceChangeEvent(playerUuid, oldBalance, amount, BalanceChangeEvent.Cause.ADMIN, reason != null ? reason : "Set balance"));
                if (event.isCancelled()) {
                    return;
                }
                balance.setBalance(amount, reason);
                this.dirtyPlayers.add(playerUuid);
                BalanceHud.updatePlayerHud(playerUuid, amount, amount - oldBalance);
                TransactionType type = reason != null && reason.contains("reset") ? TransactionType.RESET : TransactionType.SET;
                this.transactionLogger.logAction(type, playerUuid, this.resolvePlayerName(playerUuid), amount);
            }
        } finally {
            lock.unlock();
        }
    }

    public TransferResult transfer(@Nonnull UUID from, @Nonnull UUID to, double amount, String reason) {
        if (from.equals(to)) {
            return TransferResult.SELF_TRANSFER;
        }
        if (amount <= 0.0) {
            return TransferResult.INVALID_AMOUNT;
        }
        double fee = amount * Main.CONFIG.get().getTransferFee();
        double total = amount + fee;
        UUID first = from.compareTo(to) < 0 ? from : to;
        UUID second = from.compareTo(to) < 0 ? to : from;
        ReentrantLock lock1 = this.getLock(first);
        ReentrantLock lock2 = this.getLock(second);
        lock1.lock();
        try {
            PlayerBalance toBalance;
            PlayerBalance fromBalance;
            block13:
            {
                block12:
                {
                    lock2.lock();
                    try {
                        fromBalance = this.getOrLoadAccount(from);
                        toBalance = this.getOrLoadAccount(to);
                        if (fromBalance != null && fromBalance.hasBalance(total)) break block12;
                        TransferResult transferResult = TransferResult.INSUFFICIENT_FUNDS;
                        lock2.unlock();
                        return transferResult;
                    } catch (Throwable throwable) {
                        lock2.unlock();
                        throw throwable;
                    }
                }
                double maxBalance = (Main.CONFIG.get()).getMaxBalance();
                if (toBalance == null || !(toBalance.getBalance() + amount > maxBalance)) break block13;
                TransferResult transferResult = TransferResult.RECIPIENT_MAX_BALANCE;
                lock2.unlock();
                return transferResult;
            }
            fromBalance.withdrawInternal(total, "Transfer to " + to + ": " + reason);
            toBalance.depositInternal(amount, "Transfer from " + from + ": " + reason);
            this.dirtyPlayers.add(from);
            this.dirtyPlayers.add(to);

            this.transactionLogger.logTransfer(from, this.resolvePlayerName(from), to, this.resolvePlayerName(to), amount);
            TransferResult transferResult = TransferResult.SUCCESS;

            BalanceHud.updatePlayerHud(to, toBalance.getBalance(), amount);
            BalanceHud.updatePlayerHud(from, fromBalance.getBalance(), -amount);

            lock2.unlock();
            return transferResult;
        } finally {
            lock1.unlock();
        }
    }

    public Map<UUID, PlayerBalance> getAllBalances() {
        return new HashMap<>(this.cache);
    }

    public int getCachedPlayerCount() {
        return this.cache.size();
    }

    public void markDirty(@Nonnull UUID playerUuid) {
        this.dirtyPlayers.add(playerUuid);
    }

    public void forceSave() {
        this.saveDirtyPlayers();
    }

    private void autoSaveLoop() {
        while (this.running) {
            try {
                LockSupport.parkNanos((long) Main.CONFIG.get().getAutoSaveInterval() * 1000L);
                if (!this.dirtyPlayers.isEmpty()) {
                    this.saveDirtyPlayers();
                }
                if (System.currentTimeMillis() - this.lastLockCleanup <= LOCK_CLEANUP_INTERVAL_MS) continue;
                this.cleanupStaleLocks();
                this.lastLockCleanup = System.currentTimeMillis();
            } catch (Exception e) {
                break;
            }
        }
    }

    private void saveDirtyPlayers() {
        if (this.dirtyPlayers.isEmpty()) {
            return;
        }
        HashSet<UUID> toSave = new HashSet<UUID>(this.dirtyPlayers);
        this.dirtyPlayers.clear();
        HashMap<UUID, PlayerBalance> dirty = new HashMap<UUID, PlayerBalance>();
        for (UUID uuid : toSave) {
            PlayerBalance balance = this.cache.get(uuid);
            if (balance == null) continue;
            dirty.put(uuid, balance);
        }
        this.storage.saveAll(dirty).exceptionally(e -> {
            this.logger.at(Level.SEVERE).log("Auto-save failed: %s", e.getMessage());
            this.dirtyPlayers.addAll(toSave);
            return null;
        });
    }

    public void shutdown() {
        this.logger.at(Level.INFO).log("EconomyManager shutdown starting... (%d dirty, %d cached)", this.dirtyPlayers.size(), this.cache.size());
        this.running = false;
        this.logger.at(Level.INFO).log("Interrupting auto-save thread...");
        this.saveThread.interrupt();
        if (!this.cache.isEmpty()) {
            this.logger.at(Level.INFO).log("Saving %d player balances...", this.cache.size());
            try {
                if (this.storage instanceof H2StorageProvider h2) {
                    h2.saveAllSync(this.cache);
                    this.logger.at(Level.INFO).log("Player balances saved successfully (sync)");
                } else {
                    this.storage.saveAll(this.cache).get(10L, TimeUnit.SECONDS);
                    this.logger.at(Level.INFO).log("Player balances saved successfully");
                }
            } catch (TimeoutException e) {
                this.logger.at(Level.WARNING).log("Save operation timed out after 10 seconds - data may be lost");
            } catch (Exception e) {
                this.logger.at(Level.SEVERE).log("Error saving player balances: %s", e.getMessage());
            }
        }
        this.logger.at(Level.INFO).log("Shutting down storage provider...");
        try {
            this.storage.shutdown().get(5L, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            this.logger.at(Level.WARNING).log("Storage shutdown timed out after 5 seconds");
        } catch (Exception e) {
            this.logger.at(Level.SEVERE).log("Error during storage shutdown: %s", e.getMessage());
        }
        this.logger.at(Level.INFO).log("EconomyManager shutdown complete");
    }

    public StorageProvider getStorage() {
        return this.storage;
    }

    public H2StorageProvider getH2Storage() {
        H2StorageProvider h2;
        StorageProvider storageProvider = this.storage;
        return storageProvider instanceof H2StorageProvider ? (h2 = (H2StorageProvider) storageProvider) : null;
    }

    public TransactionLogger getTransactionLogger() {
        return this.transactionLogger;
    }

    private void bulkPreload() {
        try {
            Map<UUID, PlayerBalance> all = this.storage.loadAll().join();
            this.cache.putAll(all);
            this.logger.at(Level.INFO).log("Bulk preloaded %d player balances", all.size());
        } catch (Exception e) {
            this.logger.at(Level.WARNING).log("Bulk preload failed, will load on-demand: %s", (Object) e.getMessage());
        }
    }

    public List<Map.Entry<UUID, PlayerBalance>> getLeaderboard(int limit) {
        long now = System.currentTimeMillis();
        if (this.cachedLeaderboard == null || now - this.lastLeaderboardRebuild > LEADERBOARD_CACHE_MS) {
            this.cachedLeaderboard = this.cache.entrySet().stream().sorted((a, b) -> Double.compare(((PlayerBalance) b.getValue()).getBalance(), (a.getValue()).getBalance())).limit(MAX_LEADERBOARD_CACHE_SIZE).collect(Collectors.toList());
            this.lastLeaderboardRebuild = now;
        }
        return this.cachedLeaderboard.stream().limit(limit).collect(Collectors.toList());
    }

    private void cleanupStaleLocks() {
        Set onlinePlayers = Universe.get().getPlayers().stream().map(PlayerRef::getUuid).collect(Collectors.toSet());
        int removed = 0;
        for (UUID uuid : new HashSet<>(this.playerLocks.keySet())) {
            ReentrantLock lock;
            if (onlinePlayers.contains(uuid) || (lock = this.playerLocks.get(uuid)) == null || lock.isLocked())
                continue;
            this.playerLocks.remove(uuid);
            ++removed;
        }
        if (removed > 0) {
            this.logger.at(Level.FINE).log("Cleaned up %d stale player locks", removed);
        }
    }

    private String resolvePlayerName(UUID uuid) {
        PlayerRef player = Universe.get().getPlayer(uuid);
        if (player != null) {
            return player.getUsername();
        }
        return uuid.toString().substring(0, 8) + "...";
    }

    public static enum TransferResult {
        SUCCESS,
        INSUFFICIENT_FUNDS,
        SELF_TRANSFER,
        INVALID_AMOUNT,
        RECIPIENT_MAX_BALANCE;

    }
}

