/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonParser
 *  com.hypixel.hytale.logger.HytaleLogger
 *  javax.annotation.Nullable
 */
package com.ecotale.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.logger.HytaleLogger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.annotation.Nullable;

public class PlayerDBService {
    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("Ecotale-PlayerDB");
    private static final String API_URL = "https://playerdb.co/api/player/hytale/";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5L)).build();
    private static final ConcurrentHashMap<String, CacheEntry<UUID>> uuidCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, CacheEntry<String>> nameCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5L);
    private static final int MAX_CACHE_SIZE = 1000;
    private static volatile long lastRequestTime = 0L;
    private static final long MIN_REQUEST_INTERVAL_MS = 100L;

    public static CompletableFuture<UUID> lookupUuid(String name) {
        if (name == null || name.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        String lowerName = name.toLowerCase();
        CacheEntry<UUID> cached = uuidCache.get(lowerName);
        if (cached != null && !cached.isExpired()) {
            return CompletableFuture.completedFuture(cached.value);
        }
        return PlayerDBService.queryApi(name).thenApply(result -> {
            if (result != null) {
                uuidCache.put(lowerName, new CacheEntry<UUID>(result.uuid));
                nameCache.put(result.uuid, new CacheEntry<String>(result.username));
                if (uuidCache.size() > 1000) {
                    PlayerDBService.evictOldEntries();
                }
                return result.uuid;
            }
            return null;
        });
    }

    public static CompletableFuture<String> lookupName(UUID uuid) {
        if (uuid == null) {
            return CompletableFuture.completedFuture(null);
        }
        CacheEntry<String> cached = nameCache.get(uuid);
        if (cached != null && !cached.isExpired()) {
            return CompletableFuture.completedFuture(cached.value);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Nullable
    public static String getCachedName(UUID uuid) {
        CacheEntry<String> cached = nameCache.get(uuid);
        return cached != null && !cached.isExpired() ? cached.value : null;
    }

    private static CompletableFuture<PlayerInfo> queryApi(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long now = System.currentTimeMillis();
                long timeSinceLastRequest = now - lastRequestTime;
                if (timeSinceLastRequest < 100L) {
                    Thread.sleep(100L - timeSinceLastRequest);
                }
                lastRequestTime = System.currentTimeMillis();
                LOGGER.at(Level.FINE).log("Querying PlayerDB for: %s", name);
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(API_URL + name)).timeout(Duration.ofSeconds(5L)).header("User-Agent", "Ecotale-Hytale-Plugin/1.0").GET().build();
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (json.has("success") && json.get("success").getAsBoolean() && json.has("code") && "player.found".equals(json.get("code").getAsString())) {
                        JsonObject data = json.getAsJsonObject("data");
                        JsonObject player = data.getAsJsonObject("player");
                        String uuidStr = player.get("id").getAsString();
                        String username = player.get("username").getAsString();
                        UUID uuid = UUID.fromString(uuidStr);
                        LOGGER.at(Level.INFO).log("PlayerDB found: %s -> %s", username, uuid);
                        return new PlayerInfo(uuid, username);
                    }
                } else if (response.statusCode() == 404) {
                    LOGGER.at(Level.FINE).log("Player not found on PlayerDB: %s", name);
                } else {
                    LOGGER.at(Level.WARNING).log("PlayerDB API error %d for %s", response.statusCode(), name);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).log("Failed to query PlayerDB for %s: %s", name, e.getMessage());
            }
            return null;
        });
    }

    private static void evictOldEntries() {
        long now = System.currentTimeMillis();
        uuidCache.entrySet().removeIf(e -> e.getValue().isExpired(now));
        nameCache.entrySet().removeIf(e -> e.getValue().isExpired(now));
    }

    public static void clearCache() {
        uuidCache.clear();
        nameCache.clear();
    }

    private static class CacheEntry<T> {
        final T value;
        final long timestamp;

        CacheEntry(T value) {
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return this.isExpired(System.currentTimeMillis());
        }

        boolean isExpired(long now) {
            return now - this.timestamp > CACHE_TTL_MS;
        }
    }

    private record PlayerInfo(UUID uuid, String username) {
    }
}

