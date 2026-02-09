package com.ecotale.storage;

import com.ecotale.Main;
import com.ecotale.config.EcotaleConfig;
import com.ecotale.economy.PlayerBalance;
import com.ecotale.economy.TopBalanceEntry;
import com.ecotale.economy.TransactionEntry;
import com.ecotale.economy.TransactionType;
import com.ecotale.storage.StorageProvider;
import com.hypixel.hytale.logger.HytaleLogger;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import javax.annotation.Nonnull;

public class MySQLStorageProvider
implements StorageProvider {
    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("Ecotale-MySQL");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Ecotale-MySQL-IO");
        t.setDaemon(false);
        return t;
    });
    private Connection connection;
    private String tablePrefix;
    private int playerCount = 0;

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                EcotaleConfig config = (EcotaleConfig)Main.CONFIG.get();
                this.tablePrefix = config.getMysqlTablePrefix();
                String host = config.getMysqlHost();
                int port = config.getMysqlPort();
                String database = config.getMysqlDatabase();
                String username = config.getMysqlUsername();
                String password = config.getMysqlPassword();
                String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true", host, port, database);
                LOGGER.at(Level.INFO).log("Connecting to MySQL: %s:%d/%s", (Object)host, (Object)port, (Object)database);
                Class.forName("com.mysql.cj.jdbc.Driver");
                this.connection = DriverManager.getConnection(url, username, password);
                this.createTables();
                try (Statement stmt = this.connection.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + this.tablePrefix + "balances");){
                    if (rs.next()) {
                        this.playerCount = rs.getInt(1);
                    }
                }
                LOGGER.at(Level.INFO).log("MySQL connected successfully (%d players)", this.playerCount);
            }
            catch (ClassNotFoundException e) {
                LOGGER.at(Level.SEVERE).log("MySQL driver not found!");
                throw new RuntimeException("MySQL driver not available", e);
            }
            catch (SQLException e) {
                LOGGER.at(Level.SEVERE).log("Failed to connect to MySQL: %s", (Object)e.getMessage());
                throw new RuntimeException("MySQL connection failed", e);
            }
        }, this.executor);
    }

    private void createTables() throws SQLException {
        try (Statement stmt = this.connection.createStatement();){
            stmt.execute("CREATE TABLE IF NOT EXISTS %sbalances (\n    uuid VARCHAR(36) PRIMARY KEY,\n    player_name VARCHAR(64),\n    balance DOUBLE DEFAULT 0.0,\n    total_earned DOUBLE DEFAULT 0.0,\n    total_spent DOUBLE DEFAULT 0.0,\n    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n    INDEX idx_player_name (player_name),\n    INDEX idx_balance (balance DESC)\n)\n".formatted(this.tablePrefix));
            stmt.execute("CREATE TABLE IF NOT EXISTS %stransactions (\n    id BIGINT AUTO_INCREMENT PRIMARY KEY,\n    timestamp BIGINT NOT NULL,\n    type VARCHAR(20) NOT NULL,\n    source_uuid VARCHAR(36),\n    target_uuid VARCHAR(36),\n    player_name VARCHAR(64),\n    amount DOUBLE,\n    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n    INDEX idx_timestamp (timestamp DESC),\n    INDEX idx_player (player_name)\n)\n".formatted(this.tablePrefix));
            stmt.execute("CREATE TABLE IF NOT EXISTS %sbalance_snapshots (\n    id BIGINT AUTO_INCREMENT PRIMARY KEY,\n    snap_day DATE NOT NULL,\n    uuid VARCHAR(36) NOT NULL,\n    balance DOUBLE NOT NULL,\n    UNIQUE KEY uk_snap (snap_day, uuid),\n    INDEX idx_snap_day (snap_day)\n)\n".formatted(this.tablePrefix));
        }
    }

    @Override
    public CompletableFuture<PlayerBalance> loadPlayer(@Nonnull UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String sql = "SELECT balance, total_earned, total_spent FROM " + this.tablePrefix + "balances WHERE uuid = ?";
                try (PreparedStatement ps = this.connection.prepareStatement(sql);){
                    ps.setString(1, playerUuid.toString());
                    try (ResultSet rs = ps.executeQuery();){
                        if (rs.next()) {
                            PlayerBalance pb = new PlayerBalance(playerUuid);
                            pb.setBalance(rs.getDouble("balance"), "Loaded from MySQL");
                            PlayerBalance playerBalance = pb;
                            return playerBalance;
                        }
                    }
                }
                PlayerBalance newBalance = new PlayerBalance(playerUuid);
                newBalance.setBalance(((EcotaleConfig)Main.CONFIG.get()).getStartingBalance(), "Initial balance");
                ++this.playerCount;
                return newBalance;
            }
            catch (SQLException e) {
                LOGGER.at(Level.SEVERE).log("Failed to load player %s: %s", (Object)playerUuid, (Object)e.getMessage());
                return new PlayerBalance(playerUuid);
            }
        }, this.executor);
    }

    @Override
    public CompletableFuture<Void> savePlayer(@Nonnull UUID playerUuid, @Nonnull PlayerBalance balance) {
        return CompletableFuture.runAsync(() -> this.savePlayerSync(playerUuid, balance), this.executor);
    }

    private void savePlayerSync(UUID playerUuid, PlayerBalance balance) {
        try {
            String sql = "INSERT INTO %sbalances (uuid, balance, total_earned, total_spent, updated_at)\nVALUES (?, ?, ?, ?, NOW())\nON DUPLICATE KEY UPDATE\n    balance = VALUES(balance),\n    total_earned = VALUES(total_earned),\n    total_spent = VALUES(total_spent),\n    updated_at = NOW()\n".formatted(this.tablePrefix);
            try (PreparedStatement ps = this.connection.prepareStatement(sql);){
                ps.setString(1, playerUuid.toString());
                ps.setDouble(2, balance.getBalance());
                ps.setDouble(3, balance.getTotalEarned());
                ps.setDouble(4, balance.getTotalSpent());
                ps.executeUpdate();
            }
        }
        catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to save player %s: %s", (Object)playerUuid, (Object)e.getMessage());
        }
    }

    @Override
    public void updatePlayerName(@Nonnull UUID playerUuid, @Nonnull String playerName) {
        CompletableFuture.runAsync(() -> {
            try {
                String sql = "INSERT INTO %sbalances (uuid, player_name, balance)\nVALUES (?, ?, ?)\nON DUPLICATE KEY UPDATE player_name = VALUES(player_name)\n".formatted(this.tablePrefix);
                try (PreparedStatement ps = this.connection.prepareStatement(sql);){
                    ps.setString(1, playerUuid.toString());
                    ps.setString(2, playerName);
                    ps.setDouble(3, ((EcotaleConfig)Main.CONFIG.get()).getStartingBalance());
                    ps.executeUpdate();
                }
            }
            catch (SQLException e) {
                LOGGER.at(Level.WARNING).log("Failed to update player name: %s", (Object)e.getMessage());
            }
        }, this.executor);
    }

    @Override
    public CompletableFuture<String> getPlayerNameAsync(@Nonnull UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String sql = "SELECT player_name FROM " + this.tablePrefix + "balances WHERE uuid = ?";
                try (PreparedStatement ps = this.connection.prepareStatement(sql);){
                    ps.setString(1, playerUuid.toString());
                    try (ResultSet rs = ps.executeQuery();){
                        if (!rs.next()) return null;
                        String string = rs.getString("player_name");
                        return string;
                    }
                }
            }
            catch (SQLException e) {
                LOGGER.at(Level.WARNING).log("Failed to get player name: %s", (Object)e.getMessage());
            }
            return null;
        }, this.executor);
    }

    public CompletableFuture<UUID> getPlayerUuidByName(@Nonnull String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String sql = "SELECT uuid FROM " + this.tablePrefix + "balances WHERE LOWER(player_name) = LOWER(?)";
                try (PreparedStatement ps = this.connection.prepareStatement(sql);){
                    ps.setString(1, playerName);
                    try (ResultSet rs = ps.executeQuery();){
                        if (!rs.next()) return null;
                        UUID uUID = UUID.fromString(rs.getString("uuid"));
                        return uUID;
                    }
                }
            }
            catch (SQLException e) {
                LOGGER.at(Level.WARNING).log("Failed to get UUID by name: %s", (Object)e.getMessage());
            }
            return null;
        }, this.executor);
    }

    public Map<UUID, String> getAllPlayerNamesSync() {
        HashMap<UUID, String> result = new HashMap<UUID, String>();
        try {
            String sql = "SELECT uuid, player_name FROM " + this.tablePrefix + "balances WHERE player_name IS NOT NULL";
            try (Statement stmt = this.connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql);){
                while (rs.next()) {
                    String name = rs.getString("player_name");
                    if (name == null || name.isBlank()) continue;
                    result.put(UUID.fromString(rs.getString("uuid")), name);
                }
            }
        }
        catch (SQLException e) {
            LOGGER.at(Level.WARNING).log("Failed to get all player names: %s", (Object)e.getMessage());
        }
        return result;
    }

    public CompletableFuture<List<PlayerBalance>> getTopBalances(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            ArrayList<PlayerBalance> result = new ArrayList<PlayerBalance>();
            try {
                String sql = "SELECT uuid, balance FROM " + this.tablePrefix + "balances ORDER BY balance DESC LIMIT ?";
                try (PreparedStatement ps = this.connection.prepareStatement(sql);){
                    ps.setInt(1, limit);
                    try (ResultSet rs = ps.executeQuery();){
                        while (rs.next()) {
                            UUID uuid = UUID.fromString(rs.getString("uuid"));
                            PlayerBalance pb = new PlayerBalance(uuid);
                            pb.setBalance(rs.getDouble("balance"), "Top query");
                            result.add(pb);
                        }
                    }
                }
            }
            catch (SQLException e) {
                LOGGER.at(Level.WARNING).log("Failed to get top balances: %s", (Object)e.getMessage());
            }
            return result;
        }, this.executor);
    }

    public CompletableFuture<List<TopBalanceEntry>> queryTopBalancesAsync(int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            ArrayList<TopBalanceEntry> result = new ArrayList<TopBalanceEntry>();
            try {
                String sql = "SELECT uuid, player_name, balance FROM " + this.tablePrefix + "balances ORDER BY balance DESC LIMIT ? OFFSET ?";
                try (PreparedStatement ps = this.connection.prepareStatement(sql);){
                    ps.setInt(1, limit);
                    ps.setInt(2, offset);
                    try (ResultSet rs = ps.executeQuery();){
                        while (rs.next()) {
                            result.add(new TopBalanceEntry(UUID.fromString(rs.getString("uuid")), rs.getString("player_name"), rs.getDouble("balance"), 0.0));
                        }
                    }
                }
            }
            catch (SQLException e) {
                LOGGER.at(Level.WARNING).log("Failed to query top balances: %s", (Object)e.getMessage());
            }
            return result;
        }, this.executor);
    }

    public CompletableFuture<List<TopBalanceEntry>> queryTopBalancesPeriodAsync(int limit, int offset, int daysAgo) {
        return CompletableFuture.supplyAsync(() -> {
            ArrayList<TopBalanceEntry> result = new ArrayList<TopBalanceEntry>();
            try {
                String sql = "SELECT b.uuid, b.player_name, b.balance,\n       COALESCE(b.balance - s.balance, 0) as trend\nFROM %sbalances b\nLEFT JOIN %sbalance_snapshots s ON b.uuid = s.uuid AND s.snap_day = DATE_SUB(CURDATE(), INTERVAL ? DAY)\nORDER BY trend DESC\nLIMIT ? OFFSET ?\n".formatted(this.tablePrefix, this.tablePrefix);
                try (PreparedStatement ps = this.connection.prepareStatement(sql);){
                    ps.setInt(1, daysAgo);
                    ps.setInt(2, limit);
                    ps.setInt(3, offset);
                    try (ResultSet rs = ps.executeQuery();){
                        while (rs.next()) {
                            result.add(new TopBalanceEntry(UUID.fromString(rs.getString("uuid")), rs.getString("player_name"), rs.getDouble("balance"), rs.getDouble("trend")));
                        }
                    }
                }
            }
            catch (SQLException e) {
                LOGGER.at(Level.WARNING).log("Failed to query period balances: %s", (Object)e.getMessage());
            }
            return result;
        }, this.executor);
    }

    public CompletableFuture<Void> snapshotTodayAsync() {
        return this.snapshotForDateAsync(LocalDate.now());
    }

    public CompletableFuture<Void> snapshotForDateAsync(@Nonnull LocalDate date) {
        return CompletableFuture.runAsync(() -> {
            try {
                String sql = "INSERT INTO %sbalance_snapshots (snap_day, uuid, balance)\nSELECT ?, uuid, balance FROM %sbalances\nON DUPLICATE KEY UPDATE balance = VALUES(balance)\n".formatted(this.tablePrefix, this.tablePrefix);
                try (PreparedStatement ps = this.connection.prepareStatement(sql);){
                    ps.setDate(1, Date.valueOf(date));
                    ps.executeUpdate();
                }
            }
            catch (SQLException e) {
                LOGGER.at(Level.WARNING).log("Failed to snapshot balances: %s", (Object)e.getMessage());
            }
        }, this.executor);
    }

    public CompletableFuture<Integer> countPlayersAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String sql = "SELECT COUNT(*) AS total FROM " + this.tablePrefix + "balances";
                try (Statement stmt = this.connection.createStatement();
                     ResultSet rs = stmt.executeQuery(sql);){
                    if (!rs.next()) return 0;
                    Integer n = rs.getInt("total");
                    return n;
                }
            }
            catch (SQLException e) {
                LOGGER.at(Level.WARNING).log("Failed to count players: %s", (Object)e.getMessage());
            }
            return 0;
        }, this.executor);
    }

    public CompletableFuture<Integer> countPlayersWithBalanceGreaterAsync(double balance) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String sql = "SELECT COUNT(*) AS total FROM " + this.tablePrefix + "balances WHERE balance > ?";
                try (PreparedStatement ps = this.connection.prepareStatement(sql);){
                    ps.setDouble(1, balance);
                    try (ResultSet rs = ps.executeQuery();){
                        if (!rs.next()) return 0;
                        Integer n = rs.getInt("total");
                        return n;
                    }
                }
            }
            catch (SQLException e) {
                LOGGER.at(Level.WARNING).log("Failed to count balance rank: %s", (Object)e.getMessage());
            }
            return 0;
        }, this.executor);
    }

    @Override
    public CompletableFuture<Void> saveAll(@Nonnull Map<UUID, PlayerBalance> dirtyPlayers) {
        return CompletableFuture.runAsync(() -> this.saveAllSync(dirtyPlayers), this.executor);
    }

    public void saveAllSync(@Nonnull Map<UUID, PlayerBalance> dirtyPlayers) {
        if (dirtyPlayers.isEmpty()) {
            return;
        }
        try {
            this.connection.setAutoCommit(false);
            String sql = "INSERT INTO %sbalances (uuid, balance, total_earned, total_spent, updated_at)\nVALUES (?, ?, ?, ?, NOW())\nON DUPLICATE KEY UPDATE\n    balance = VALUES(balance),\n    total_earned = VALUES(total_earned),\n    total_spent = VALUES(total_spent),\n    updated_at = NOW()\n".formatted(this.tablePrefix);
            try (PreparedStatement ps = this.connection.prepareStatement(sql);){
                for (Map.Entry<UUID, PlayerBalance> entry : dirtyPlayers.entrySet()) {
                    ps.setString(1, entry.getKey().toString());
                    ps.setDouble(2, entry.getValue().getBalance());
                    ps.setDouble(3, entry.getValue().getTotalEarned());
                    ps.setDouble(4, entry.getValue().getTotalSpent());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            this.connection.commit();
            LOGGER.at(Level.INFO).log("Saved %d player balances to MySQL", dirtyPlayers.size());
        }
        catch (SQLException e) {
            try {
                this.connection.rollback();
            }
            catch (SQLException sQLException) {
                // empty catch block
            }
            LOGGER.at(Level.SEVERE).log("Failed to batch save: %s", (Object)e.getMessage());
        }
        finally {
            try {
                this.connection.setAutoCommit(true);
            }
            catch (SQLException sQLException) {}
        }
    }

    @Override
    public CompletableFuture<Map<UUID, PlayerBalance>> loadAll() {
        return CompletableFuture.supplyAsync(() -> {
            HashMap<UUID, PlayerBalance> result = new HashMap<UUID, PlayerBalance>();
            try {
                String sql = "SELECT uuid, balance, total_earned, total_spent FROM " + this.tablePrefix + "balances";
                try (Statement stmt = this.connection.createStatement();
                     ResultSet rs = stmt.executeQuery(sql);){
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        PlayerBalance pb = new PlayerBalance(uuid);
                        pb.setBalance(rs.getDouble("balance"), "Bulk load");
                        result.put(uuid, pb);
                    }
                }
                this.playerCount = result.size();
            }
            catch (SQLException e) {
                LOGGER.at(Level.SEVERE).log("Failed to load all balances: %s", (Object)e.getMessage());
            }
            return result;
        }, this.executor);
    }

    @Override
    public CompletableFuture<Boolean> playerExists(@Nonnull UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String sql = "SELECT 1 FROM " + this.tablePrefix + "balances WHERE uuid = ?";
                try (PreparedStatement ps = this.connection.prepareStatement(sql);){
                    Boolean bl;
                    block14: {
                        ps.setString(1, playerUuid.toString());
                        ResultSet rs = ps.executeQuery();
                        try {
                            bl = rs.next();
                            if (rs == null) break block14;
                        }
                        catch (Throwable t$) {
                            if (rs != null) {
                                try {
                                    rs.close();
                                }
                                catch (Throwable x2) {
                                    t$.addSuppressed(x2);
                                }
                            }
                            throw t$;
                        }
                        rs.close();
                    }
                    return bl;
                }
            }
            catch (SQLException e) {
                return false;
            }
        }, this.executor);
    }

    @Override
    public CompletableFuture<Void> deletePlayer(@Nonnull UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            try {
                String sql = "DELETE FROM " + this.tablePrefix + "balances WHERE uuid = ?";
                try (PreparedStatement ps = this.connection.prepareStatement(sql);){
                    ps.setString(1, playerUuid.toString());
                    int affected = ps.executeUpdate();
                    if (affected > 0) {
                        --this.playerCount;
                    }
                }
            }
            catch (SQLException e) {
                LOGGER.at(Level.SEVERE).log("Failed to delete player %s: %s", (Object)playerUuid, (Object)e.getMessage());
            }
        }, this.executor);
    }

    public void logTransaction(TransactionEntry entry) {
        this.executor.execute(() -> {
            try {
                String sql = "INSERT INTO %stransactions (timestamp, type, source_uuid, target_uuid, player_name, amount)\nVALUES (?, ?, ?, ?, ?, ?)\n".formatted(this.tablePrefix);
                try (PreparedStatement ps = this.connection.prepareStatement(sql);){
                    ps.setLong(1, entry.timestamp().toEpochMilli());
                    ps.setString(2, entry.type().name());
                    ps.setString(3, entry.sourcePlayer() != null ? entry.sourcePlayer().toString() : null);
                    ps.setString(4, entry.targetPlayer() != null ? entry.targetPlayer().toString() : null);
                    ps.setString(5, entry.playerName());
                    ps.setDouble(6, entry.amount());
                    ps.executeUpdate();
                }
            }
            catch (SQLException e) {
                LOGGER.at(Level.WARNING).log("Failed to log transaction: %s", (Object)e.getMessage());
            }
        });
    }

    public CompletableFuture<List<TransactionEntry>> queryTransactionsAsync(String playerFilter, int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            ArrayList<TransactionEntry> results = new ArrayList<TransactionEntry>();
            try {
                String sql = playerFilter != null && !playerFilter.isEmpty() ? "SELECT * FROM %stransactions\nWHERE LOWER(player_name) LIKE ?\nORDER BY timestamp DESC\nLIMIT ? OFFSET ?\n".formatted(this.tablePrefix) : "SELECT * FROM %stransactions\nORDER BY timestamp DESC\nLIMIT ? OFFSET ?\n".formatted(this.tablePrefix);
                try (PreparedStatement ps = this.connection.prepareStatement(sql);){
                    int paramIndex = 1;
                    if (playerFilter != null && !playerFilter.isEmpty()) {
                        ps.setString(paramIndex++, "%" + playerFilter.toLowerCase() + "%");
                    }
                    ps.setInt(paramIndex++, limit);
                    ps.setInt(paramIndex, offset);
                    try (ResultSet rs = ps.executeQuery();){
                        while (rs.next()) {
                            results.add(this.resultSetToEntry(rs));
                        }
                    }
                }
            }
            catch (SQLException e) {
                LOGGER.at(Level.WARNING).log("Failed to query transactions: %s", (Object)e.getMessage());
            }
            return results;
        }, this.executor);
    }

    public CompletableFuture<Integer> countTransactionsAsync(String playerFilter) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String sql = playerFilter != null && !playerFilter.isEmpty() ? "SELECT COUNT(*) FROM " + this.tablePrefix + "transactions WHERE LOWER(player_name) LIKE ?" : "SELECT COUNT(*) FROM " + this.tablePrefix + "transactions";
                try (PreparedStatement ps = this.connection.prepareStatement(sql);){
                    if (playerFilter != null && !playerFilter.isEmpty()) {
                        ps.setString(1, "%" + playerFilter.toLowerCase() + "%");
                    }
                    try (ResultSet rs = ps.executeQuery();){
                        if (!rs.next()) return 0;
                        Integer n = rs.getInt(1);
                        return n;
                    }
                }
            }
            catch (SQLException e) {
                LOGGER.at(Level.WARNING).log("Failed to count transactions: %s", (Object)e.getMessage());
            }
            return 0;
        }, this.executor);
    }

    private TransactionEntry resultSetToEntry(ResultSet rs) throws SQLException {
        long timestampMs = rs.getLong("timestamp");
        Instant timestamp = Instant.ofEpochMilli(timestampMs);
        TransactionType type = TransactionType.valueOf(rs.getString("type"));
        String sourceUuidStr = rs.getString("source_uuid");
        UUID sourceUuid = sourceUuidStr != null ? UUID.fromString(sourceUuidStr) : null;
        String targetUuidStr = rs.getString("target_uuid");
        UUID targetUuid = targetUuidStr != null ? UUID.fromString(targetUuidStr) : null;
        String playerName = rs.getString("player_name");
        double amount = rs.getDouble("amount");
        String formattedTime = TIME_FORMATTER.format(timestamp);
        return new TransactionEntry(timestamp, formattedTime, type, sourceUuid, targetUuid, amount, playerName);
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        this.executor.shutdown();
        LOGGER.at(Level.INFO).log("MySQL shutdown: closing connection...");
        try {
            if (this.connection != null && !this.connection.isClosed()) {
                this.connection.close();
            }
            LOGGER.at(Level.INFO).log("MySQL connection closed");
        }
        catch (SQLException e) {
            LOGGER.at(Level.WARNING).log("Error closing MySQL connection: %s", (Object)e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public String getName() {
        return "MySQL (shared database)";
    }

    @Override
    public int getPlayerCount() {
        return this.playerCount;
    }
}

