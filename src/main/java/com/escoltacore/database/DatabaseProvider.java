package com.escoltacore.database;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Abstraction layer for storage backends.
 * Implementations: SQLiteProvider, MySQLProvider.
 */
public interface DatabaseProvider {

    /** Initialize tables and connection pool. */
    void init();

    /** Close connections gracefully. */
    void close();

    /** Increments games played counter. */
    CompletableFuture<Void> addGamePlayed(UUID uuid);

    /** Increments games won counter. */
    CompletableFuture<Void> addGameWon(UUID uuid);

    /** Increments games lost counter. */
    CompletableFuture<Void> addGameLost(UUID uuid);

    /** Returns the full stats for a player. */
    CompletableFuture<PlayerStats> getStats(UUID uuid);

    // ── Inner record ──────────────────────────────────────────────────────────

    record PlayerStats(UUID uuid, int played, int won, int lost) {
        public double winRate() {
            return played == 0 ? 0.0 : (won * 100.0) / played;
        }
    }
}
