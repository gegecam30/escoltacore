package com.escoltacore.database;

import com.escoltacore.EscoltaCorePlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public class MySQLProvider implements DatabaseProvider {

    private final EscoltaCorePlugin plugin;
    private Connection connection;
    private final ExecutorService pool;

    public MySQLProvider(EscoltaCorePlugin plugin) {
        this.plugin = plugin;
        FileConfiguration cfg = plugin.getConfig();
        this.pool = Executors.newFixedThreadPool(cfg.getInt("mysql.pool-size", 5));
    }

    @Override
    public void init() {
        FileConfiguration cfg = plugin.getConfig();
        String host     = cfg.getString("mysql.host", "localhost");
        int    port     = cfg.getInt("mysql.port", 3306);
        String database = cfg.getString("mysql.database", "escoltacore");
        String user     = cfg.getString("mysql.username", "root");
        String pass     = cfg.getString("mysql.password", "");

        String url = "jdbc:mysql://%s:%d/%s?autoReconnect=true&useSSL=false"
                .formatted(host, port, database);
        try {
            connection = DriverManager.getConnection(url, user, pass);
            createTable();
            plugin.getLogger().info("MySQL database connected.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to MySQL!", e);
        }
    }

    private void createTable() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_stats (
                    uuid    VARCHAR(36) PRIMARY KEY,
                    played  INT NOT NULL DEFAULT 0,
                    won     INT NOT NULL DEFAULT 0,
                    lost    INT NOT NULL DEFAULT 0
                )
            """);
        }
    }

    @Override
    public void close() {
        pool.shutdown();
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error closing MySQL connection.", e);
        }
    }

    @Override
    public CompletableFuture<Void> addGamePlayed(UUID uuid) {
        return CompletableFuture.runAsync(() -> upsert(uuid, "played"), pool);
    }

    @Override
    public CompletableFuture<Void> addGameWon(UUID uuid) {
        return CompletableFuture.runAsync(() -> upsert(uuid, "won"), pool);
    }

    @Override
    public CompletableFuture<Void> addGameLost(UUID uuid) {
        return CompletableFuture.runAsync(() -> upsert(uuid, "lost"), pool);
    }

    @Override
    public CompletableFuture<PlayerStats> getStats(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT played, won, lost FROM player_stats WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return new PlayerStats(uuid, rs.getInt("played"),
                            rs.getInt("won"), rs.getInt("lost"));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Error reading stats.", e);
            }
            return new PlayerStats(uuid, 0, 0, 0);
        }, pool);
    }

    private void upsert(UUID uuid, String column) {
        String sql = """
            INSERT INTO player_stats (uuid, %s) VALUES (?, 1)
            ON DUPLICATE KEY UPDATE %s = %s + 1
        """.formatted(column, column, column);
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error upserting stats.", e);
        }
    }
}
