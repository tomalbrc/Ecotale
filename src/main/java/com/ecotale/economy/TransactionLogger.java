package com.ecotale.economy;

import com.ecotale.storage.H2StorageProvider;
import com.ecotale.storage.MySQLStorageProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class TransactionLogger {
    private static final int DEFAULT_BUFFER_SIZE = 500;
    private final TransactionEntry[] buffer;
    private final int bufferSize;
    private final AtomicInteger writeIndex = new AtomicInteger(0);
    private final AtomicInteger totalWrites = new AtomicInteger(0);
    private H2StorageProvider h2Storage;
    private MySQLStorageProvider mysqlStorage;
    private static TransactionLogger instance;

    public TransactionLogger() {
        this(500);
    }

    public TransactionLogger(int bufferSize) {
        this.bufferSize = bufferSize;
        this.buffer = new TransactionEntry[bufferSize];
    }

    public static TransactionLogger getInstance() {
        if (instance == null) {
            instance = new TransactionLogger();
        }
        return instance;
    }

    public static void setInstance(TransactionLogger logger) {
        instance = logger;
    }

    public void setH2Storage(H2StorageProvider storage) {
        this.h2Storage = storage;
    }

    public void setMysqlStorage(MySQLStorageProvider storage) {
        this.mysqlStorage = storage;
    }

    public void logAction(TransactionType type, UUID player, String playerName, double amount) {
        this.log(TransactionEntry.single(type, player, playerName, amount));
    }

    public void logTransfer(UUID from, String fromName, UUID to, String toName, double amount) {
        this.log(TransactionEntry.transfer(from, fromName, to, toName, amount));
    }

    private void log(TransactionEntry entry) {
        int idx = this.writeIndex.getAndIncrement() % this.bufferSize;
        this.buffer[idx] = entry;
        this.totalWrites.incrementAndGet();
        if (this.h2Storage != null) {
            this.h2Storage.logTransaction(entry);
        }
        if (this.mysqlStorage != null) {
            this.mysqlStorage.logTransaction(entry);
        }
    }

    public List<TransactionEntry> getRecent(int count) {
        int total = this.totalWrites.get();
        int available = Math.min(total, this.bufferSize);
        int toReturn = Math.min(count, available);
        if (toReturn == 0) {
            return Collections.emptyList();
        }
        ArrayList<TransactionEntry> result = new ArrayList<TransactionEntry>(toReturn);
        int currentIdx = (this.writeIndex.get() - 1 + this.bufferSize) % this.bufferSize;
        for (int i = 0; i < toReturn; ++i) {
            TransactionEntry entry = this.buffer[currentIdx];
            if (entry != null) {
                result.add(entry);
            }
            currentIdx = (currentIdx - 1 + this.bufferSize) % this.bufferSize;
        }
        return result;
    }

    public List<TransactionEntry> getAll() {
        return this.getRecent(this.bufferSize);
    }

    public List<TransactionEntry> getRecentForPlayer(UUID playerUuid, int limit) {
        List<TransactionEntry> all = this.getRecent(this.bufferSize);
        ArrayList<TransactionEntry> filtered = new ArrayList<TransactionEntry>();
        for (TransactionEntry entry : all) {
            if (!entry.involvesPlayer(playerUuid)) continue;
            filtered.add(entry);
            if (filtered.size() < limit) continue;
            break;
        }
        return filtered;
    }

    public int getTotalTransactions() {
        return this.totalWrites.get();
    }

    public int getBufferSize() {
        return this.bufferSize;
    }

    public int getAvailableCount() {
        return Math.min(this.totalWrites.get(), this.bufferSize);
    }

    public void clear() {
        for (int i = 0; i < this.bufferSize; ++i) {
            this.buffer[i] = null;
        }
        this.writeIndex.set(0);
        this.totalWrites.set(0);
    }
}

