package com.ecotale.storage;

import com.ecotale.Main;
import com.ecotale.config.EcotaleConfig;
import com.ecotale.economy.PlayerBalance;
import com.ecotale.economy.TopBalanceEntry;
import com.ecotale.economy.TransactionEntry;
import com.ecotale.economy.TransactionType;
import com.ecotale.storage.StorageProvider;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.File;
import java.nio.file.Path;
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

public class H2StorageProvider
implements StorageProvider {
    private static final String DB_NAME = "ecotale";
    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("Ecotale-H2");
    private static final Path ECOTALE_PATH = Path.of("mods", "Ecotale_Ecotale");
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Ecotale-H2-IO");
        t.setDaemon(false);
        return t;
    });
    private Connection connection;
    private String dbPath;
    private int playerCount = 0;

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                File dataDir = ECOTALE_PATH.toFile();
                if (!dataDir.exists()) {
                    dataDir.mkdirs();
                }
                this.dbPath = new File(dataDir, DB_NAME).getAbsolutePath();
                try {
                    Class.forName("org.h2.Driver");
                }
                catch (ClassNotFoundException e) {
                    LOGGER.at(Level.SEVERE).log("H2 Driver class not found: %s", (Object)e.getMessage());
                    throw new RuntimeException("H2 Driver not available", e);
                }
                this.connection = DriverManager.getConnection("jdbc:h2:" + this.dbPath + ";MODE=MySQL;AUTO_SERVER=FALSE;DB_CLOSE_ON_EXIT=FALSE", "sa", "");
                this.createTables();
                try (Statement stmt = this.connection.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM balances");){
                    if (rs.next()) {
                        this.playerCount = rs.getInt(1);
                    }
                }
                LOGGER.at(Level.INFO).log("H2 database initialized: %s.mv.db (%d players)", (Object)this.dbPath, this.playerCount);
            }
            catch (SQLException e) {
                LOGGER.at(Level.SEVERE).log("Failed to initialize H2 database: %s", (Object)e.getMessage());
                throw new RuntimeException(e);
            }
        }, this.executor);
    }

    private void createTables() throws SQLException {
        try (Statement stmt = this.connection.createStatement();){
            stmt.execute("    CREATE TABLE IF NOT EXISTS balances (\n        uuid VARCHAR(36) PRIMARY KEY,\n        player_name VARCHAR(64),\n        balance DOUBLE DEFAULT 0.0,\n        total_earned DOUBLE DEFAULT 0.0,\n        total_spent DOUBLE DEFAULT 0.0,\n        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n    )\n");
            try {
                stmt.execute("ALTER TABLE balances ADD COLUMN IF NOT EXISTS player_name VARCHAR(64)");
            }
            catch (SQLException sQLException) {
                // empty catch block
            }
            stmt.execute("    CREATE TABLE IF NOT EXISTS transactions (\n        id BIGINT AUTO_INCREMENT PRIMARY KEY,\n        timestamp BIGINT NOT NULL,\n        type VARCHAR(20) NOT NULL,\n        source_uuid VARCHAR(36),\n        target_uuid VARCHAR(36),\n        player_name VARCHAR(64),\n        amount DOUBLE,\n        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n    )\n");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tx_timestamp ON transactions(timestamp DESC)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tx_player ON transactions(player_name)");
            stmt.execute("    CREATE TABLE IF NOT EXISTS balance_snapshots (\n        snap_day DATE NOT NULL,\n        uuid VARCHAR(36) NOT NULL,\n        balance DOUBLE DEFAULT 0.0,\n        PRIMARY KEY(snap_day, uuid)\n    )\n");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_snap_day ON balance_snapshots(snap_day)");
        }
    }

    @Override
    public CompletableFuture<PlayerBalance> loadPlayer(@Nonnull UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String sql = "SELECT balance, total_earned, total_spent FROM balances WHERE uuid = ?";
                try (PreparedStatement ps = this.connection.prepareStatement(sql);){
                    ps.setString(1, playerUuid.toString());
                    try (ResultSet rs = ps.executeQuery();){
                        if (rs.next()) {
                            PlayerBalance pb = new PlayerBalance(playerUuid);
                            pb.setBalance(rs.getDouble("balance"), "Loaded from DB");
                            PlayerBalance playerBalance = pb;
                            return playerBalance;
                        }
                    }
                }
                double startingBalance = ((EcotaleConfig)Main.CONFIG.get()).getStartingBalance();
                PlayerBalance newBalance = new PlayerBalance(playerUuid);
                newBalance.setBalance(startingBalance, "New account");
                this.savePlayerSync(playerUuid, newBalance);
                ++this.playerCount;
                return newBalance;
            }
            catch (SQLException e) {
                LOGGER.at(Level.SEVERE).log("Failed to load player %s: %s", (Object)playerUuid, (Object)e.getMessage());
                PlayerBalance pb = new PlayerBalance(playerUuid);
                pb.setBalance(((EcotaleConfig)Main.CONFIG.get()).getStartingBalance(), "Error fallback");
                return pb;
            }
        }, this.executor);
    }

    @Override
    public CompletableFuture<Void> savePlayer(@Nonnull UUID playerUuid, @Nonnull PlayerBalance balance) {
        return CompletableFuture.runAsync(() -> this.savePlayerSync(playerUuid, balance), this.executor);
    }

    private void savePlayerSync(UUID playerUuid, PlayerBalance balance) {
        try {
            String sql = "    MERGE INTO balances (uuid, balance, total_earned, total_spent, updated_at)\n    KEY(uuid)\n    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)\n";
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
            block14: {
                try {
                    String sql = "UPDATE balances SET player_name = ? WHERE uuid = ?";
                    try (PreparedStatement ps = this.connection.prepareStatement(sql);){
                        ps.setString(1, playerName);
                        ps.setString(2, playerUuid.toString());
                        int updated = ps.executeUpdate();
                        if (updated != 0) break block14;
                        String insertSql = "    INSERT INTO balances (uuid, player_name, balance)\n    VALUES (?, ?, ?)\n";
                        try (PreparedStatement insertPs = this.connection.prepareStatement(insertSql);){
                            insertPs.setString(1, playerUuid.toString());
                            insertPs.setString(2, playerName);
                            insertPs.setDouble(3, ((EcotaleConfig)Main.CONFIG.get()).getStartingBalance());
                            insertPs.executeUpdate();
                        }
                    }
                }
                catch (SQLException e) {
                    LOGGER.at(Level.WARNING).log("Failed to update player name: %s", (Object)e.getMessage());
                }
            }
        }, this.executor);
    }

    @Override
    public CompletableFuture<String> getPlayerNameAsync(@Nonnull UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String sql = "SELECT player_name FROM balances WHERE uuid = ?";
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
                String sql = "SELECT uuid FROM balances WHERE LOWER(player_name) = ? LIMIT 1";
                try (PreparedStatement ps = this.connection.prepareStatement(sql);){
                    ps.setString(1, playerName.toLowerCase());
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
            String sql = "SELECT uuid, player_name FROM balances WHERE player_name IS NOT NULL";
            try (PreparedStatement ps = this.connection.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery();){
                while (rs.next()) {
                    String uuidStr = rs.getString("uuid");
                    String name = rs.getString("player_name");
                    if (name == null || name.isBlank()) continue;
                    result.put(UUID.fromString(uuidStr), name);
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
                String sql = "SELECT uuid, balance, total_earned, total_spent FROM balances ORDER BY balance DESC LIMIT ?";
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
                LOGGER.at(Level.WARNING).log("Failed to query top balances: %s", (Object)e.getMessage());
            }
            return result;
        }, this.executor);
    }

    public CompletableFuture<List<TopBalanceEntry>> queryTopBalancesAsync(int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            ArrayList<TopBalanceEntry> result = new ArrayList<TopBalanceEntry>();
            try {
                String sql = "SELECT uuid, balance, player_name FROM balances ORDER BY balance DESC LIMIT ? OFFSET ?";
                try (PreparedStatement ps = this.connection.prepareStatement(sql);){
                    ps.setInt(1, limit);
                    ps.setInt(2, offset);
                    try (ResultSet rs = ps.executeQuery();){
                        while (rs.next()) {
                            UUID uuid = UUID.fromString(rs.getString("uuid"));
                            double balance = rs.getDouble("balance");
                            String name = rs.getString("player_name");
                            result.add(new TopBalanceEntry(uuid, name, balance, 0.0));
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
                String sql = "    SELECT b.uuid, b.balance, b.player_name,\n           (b.balance - COALESCE(s.balance, 0)) AS trend\n    FROM balances b\n                        LEFT JOIN balance_snapshots s\n                            ON s.uuid = b.uuid AND s.snap_day = ?\n    ORDER BY trend DESC\n    LIMIT ? OFFSET ?\n";
                try (PreparedStatement ps = this.connection.prepareStatement(sql);){
                    ps.setDate(1, Date.valueOf(LocalDate.now().minusDays(daysAgo)));
                    ps.setInt(2, limit);
                    ps.setInt(3, offset);
                    try (ResultSet rs = ps.executeQuery();){
                        while (rs.next()) {
                            UUID uuid = UUID.fromString(rs.getString("uuid"));
                            double balance = rs.getDouble("balance");
                            String name = rs.getString("player_name");
                            double trend = rs.getDouble("trend");
                            result.add(new TopBalanceEntry(uuid, name, balance, trend));
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
                String sql = "    MERGE INTO balance_snapshots (snap_day, uuid, balance)\n    KEY(snap_day, uuid)\n    SELECT ?, uuid, balance FROM balances\n";
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
                String sql = "SELECT COUNT(*) AS total FROM balances";
                try (PreparedStatement ps = this.connection.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery();){
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
                String sql = "SELECT COUNT(*) AS total FROM balances WHERE balance > ?";
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

    @Deprecated
    public String getPlayerName(@Nonnull UUID playerUuid) {
        return this.getPlayerNameAsync(playerUuid).join();
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
            String sql = "    MERGE INTO balances (uuid, balance, total_earned, total_spent, updated_at)\n    KEY(uuid)\n    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)\n";
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
            LOGGER.at(Level.INFO).log("Saved %d player balances to H2", dirtyPlayers.size());
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
                String sql = "SELECT uuid, balance, total_earned, total_spent FROM balances";
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
                String sql = "SELECT 1 FROM balances WHERE uuid = ?";
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
                String sql = "DELETE FROM balances WHERE uuid = ?";
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
                String sql = "    INSERT INTO transactions (timestamp, type, source_uuid, target_uuid, player_name, amount)\n    VALUES (?, ?, ?, ?, ?, ?)\n";
                try (PreparedStatement ps = this.connection.prepareStatement(sql);){
                    ps.setLong(1, entry.timestamp().toEpochMilli());
                    ps.setString(2, entry.type().name());
                    ps.setString(3, entry.sourcePlayer() != null ? entry.sourcePlayer().toString() : null);
                    ps.setString(4, entry.targetPlayer() != null ? entry.targetPlayer().toString() : null);
                    ps.setString(5, entry.playerName());
                    ps.setDouble(6, entry.amount());
                    ps.executeUpdate();
                    LOGGER.at(Level.INFO).log("Logged transaction to H2: %s %s %.0f", (Object)entry.type(), (Object)entry.playerName(), (Object)entry.amount());
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
                String sql = playerFilter != null && !playerFilter.isEmpty() ? "    SELECT * FROM transactions\n    WHERE LOWER(player_name) LIKE ?\n    ORDER BY timestamp DESC\n    LIMIT ? OFFSET ?\n" : "    SELECT * FROM transactions\n    ORDER BY timestamp DESC\n    LIMIT ? OFFSET ?\n";
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

    @Deprecated
    public List<TransactionEntry> queryTransactions(String playerFilter, int limit, int offset) {
        return this.queryTransactionsAsync(playerFilter, limit, offset).join();
    }

    public CompletableFuture<Integer> countTransactionsAsync(String playerFilter) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String sql = playerFilter != null && !playerFilter.isEmpty() ? "SELECT COUNT(*) FROM transactions WHERE LOWER(player_name) LIKE ?" : "SELECT COUNT(*) FROM transactions";
                try (PreparedStatement ps = this.connection.prepareStatement(sql);){
                    if (playerFilter != null && !playerFilter.isEmpty()) {
                        ps.setString(1, "%" + playerFilter.toLowerCase() + "%");
                    }
                    try (ResultSet rs = ps.executeQuery();){
                        if (!rs.next()) return 0;
                        int count = rs.getInt(1);
                        LOGGER.at(Level.INFO).log("H2 transaction count: %d (filter: %s)", count, (Object)playerFilter);
                        Integer n = count;
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

    @Deprecated
    public int countTransactions(String playerFilter) {
        return this.countTransactionsAsync(playerFilter).join();
    }

    private TransactionEntry resultSetToEntry(ResultSet rs) throws SQLException {
        long timestampMs = rs.getLong("timestamp");
        Instant timestamp = Instant.ofEpochMilli(timestampMs);
        String typeStr = rs.getString("type");
        TransactionType type = TransactionType.valueOf(typeStr);
        String sourceUuidStr = rs.getString("source_uuid");
        UUID sourceUuid = sourceUuidStr != null ? UUID.fromString(sourceUuidStr) : null;
        String targetUuidStr = rs.getString("target_uuid");
        UUID targetUuid = targetUuidStr != null ? UUID.fromString(targetUuidStr) : null;
        String playerName = rs.getString("player_name");
        double amount = rs.getDouble("amount");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
        String formattedTime = formatter.format(timestamp);
        return new TransactionEntry(timestamp, formattedTime, type, sourceUuid, targetUuid, amount, playerName);
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        this.executor.shutdown();
        LOGGER.at(Level.INFO).log("H2 shutdown: closing connection...");
        try {
            if (this.connection != null && !this.connection.isClosed()) {
                this.connection.close();
            }
            LOGGER.at(Level.INFO).log("H2 database connection closed");
        }
        catch (SQLException e) {
            LOGGER.at(Level.WARNING).log("Error closing H2 connection: %s", (Object)e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public String getName() {
        return "H2 Database";
    }

    @Override
    public int getPlayerCount() {
        return this.playerCount;
    }

    public Connection getConnection() {
        return this.connection;
    }
}

