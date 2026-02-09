package com.ecotale.storage;

import com.ecotale.economy.PlayerBalance;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

public interface StorageProvider {
    public CompletableFuture<Void> initialize();

    public CompletableFuture<PlayerBalance> loadPlayer(@Nonnull UUID var1);

    public CompletableFuture<Void> savePlayer(@Nonnull UUID var1, @Nonnull PlayerBalance var2);

    public CompletableFuture<Void> saveAll(@Nonnull Map<UUID, PlayerBalance> var1);

    public CompletableFuture<Map<UUID, PlayerBalance>> loadAll();

    public CompletableFuture<Boolean> playerExists(@Nonnull UUID var1);

    public CompletableFuture<Void> deletePlayer(@Nonnull UUID var1);

    public CompletableFuture<Void> shutdown();

    public String getName();

    public int getPlayerCount();

    default public CompletableFuture<UUID> getPlayerUuid(@Nonnull String playerName) {
        return CompletableFuture.completedFuture(null);
    }

    default public CompletableFuture<String> getPlayerNameAsync(@Nonnull UUID playerUuid) {
        return CompletableFuture.completedFuture(null);
    }

    default public void updatePlayerName(@Nonnull UUID playerUuid, @Nonnull String playerName) {
    }
}

