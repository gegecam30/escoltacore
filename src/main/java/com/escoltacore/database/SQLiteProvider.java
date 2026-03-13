package com.escoltacore.database;

import com.escoltacore.EscoltaCorePlugin;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class SQLiteProvider implements DatabaseProvider {

    private final EscoltaCorePlugin plugin;
    private Connection connection;

    public SQLiteProvider(EscoltaCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        File dbFile = new File(plugin.getDataFolder(), "data.db");
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            createTable();
            plugin.getLogger().info("SQLite database initialized.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize SQLite!", e);
        }
    }

    private void createTable() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_stats (
                    uuid    TEXT PRIMARY KEY,
                    played  INTEGER NOT NULL DEFAULT 0,
                    won     INTEGER NOT NULL DEFAULT 0,
                    lost    INTEGER NOT NULL DEFAULT 0
                )
            """);
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error closing SQLite connection.", e);
        }
    }

    @Override
    public CompletableFuture<Void> addGamePlayed(UUID uuid) {
        return CompletableFuture.runAsync(() -> upsertAndIncrement(uuid, "played"));
    }

    @Override
    public CompletableFuture<Void> addGameWon(UUID uuid) {
        return CompletableFuture.runAsync(() -> upsertAndIncrement(uuid, "won"));
    }

    @Override
    public CompletableFuture<Void> addGameLost(UUID uuid) {
        return CompletableFuture.runAsync(() -> upsertAndIncrement(uuid, "lost"));
    }

    @Override
    public CompletableFuture<PlayerStats> getStats(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT played, won, lost FROM player_stats WHERE uuid = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
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
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void upsertAndIncrement(UUID uuid, String column) {
        String upsert = """
            INSERT INTO player_stats (uuid, %s) VALUES (?, 1)
            ON CONFLICT(uuid) DO UPDATE SET %s = %s + 1
        """.formatted(column, column, column);
        try (PreparedStatement ps = connection.prepareStatement(upsert)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error updating stats.", e);
        }
    }
}
