package info.mester.network.proxy

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import info.mester.network.common.expandToUUID
import info.mester.network.common.shorten
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID

object DatabaseManager {
    private val dataSource: HikariDataSource
    private val connection: Connection

    init {
        val config = HikariConfig()
        config.jdbcUrl = "jdbc:mysql://localhost:3306/mesternetwork?useSSL=false"
        config.username = "proxy"
        config.password = "proxypassword"
        config.driverClassName = "com.mysql.cj.jdbc.Driver"
        dataSource = HikariDataSource(config)
        connection = dataSource.connection
        createTablesIfNotExist()
    }

    private fun createTablesIfNotExist() {
        try {
            connection.createStatement()?.use { statement ->
                // emojis table
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS emojis (
                        keyword_hash CHAR(64) PRIMARY KEY,
                        keyword TEXT NOT NULL,
                        emoji TEXT NOT NULL
                    );
                    """,
                )

                // friendships table for two-way relationships
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS friendships (
                        player1_uuid CHAR(32) BINARY NOT NULL,
                        player2_uuid CHAR(32) BINARY NOT NULL,
                        friendship_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (player1_uuid, player2_uuid),
                        CHECK (BINARY player1_uuid < BINARY player2_uuid),
                        INDEX idx_player1 (player1_uuid),
                        INDEX idx_player2 (player2_uuid)
                    ) COLLATE utf8mb4_bin;
                    """,
                )

                // names table for caching player usernames
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS names (
                        uuid CHAR(32) BINARY NOT NULL,
                        name VARCHAR(16) NOT NULL,
                        PRIMARY KEY (uuid)
                    ) COLLATE utf8mb4_bin;
                    """,
                )
            }
        } catch (e: SQLException) {
            println("Failed to create tables: ${e.message}")
        }
    }

    fun writeEmojis(emojis: List<EmojiConfig>) {
        try {
            connection.createStatement()?.use { statement ->
                statement.executeUpdate("DELETE FROM emojis")
                for (emoji in emojis) {
                    val updateStatement =
                        connection.prepareStatement(
                            "INSERT INTO emojis (keyword_hash, keyword, emoji) VALUES (SHA2(?, 256), ?, ?)",
                        )
                    updateStatement.setString(1, emoji.keyword)
                    updateStatement.setString(2, emoji.keyword)
                    updateStatement.setString(3, emoji.emoji)
                    updateStatement.executeUpdate()
                    updateStatement.close()
                }
            }
        } catch (e: SQLException) {
            println("Failed to write data: ${e.message}")
        }
    }

    /**
     * Creates a new friendship between two players or updates the timestamp if it already exists.
     * The players' UUIDs are stored in a consistent order (smaller UUID first).
     *
     * @param player1 UUID of the first player
     * @param player2 UUID of the second player
     * @return true if friendship was created/updated successfully, false if there was an error
     */
    fun writeFriendship(
        player1: UUID,
        player2: UUID,
    ): Boolean =
        try {
            val uuid1 = player1.shorten()
            val uuid2 = player2.shorten()

            val (smallerUuid, largerUuid) =
                if (uuid1 < uuid2) {
                    uuid1 to uuid2
                } else {
                    uuid2 to uuid1
                }

            connection
                .prepareStatement(
                    """
            INSERT INTO friendships (player1_uuid, player2_uuid)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE friendship_date = CURRENT_TIMESTAMP
            """,
                ).use { stmt ->
                    stmt.setString(1, smallerUuid)
                    stmt.setString(2, largerUuid)
                    stmt.executeUpdate() > 0
                }
        } catch (e: SQLException) {
            println("Failed to write friendship: ${e.message}")
            e.printStackTrace()
            false
        }

    /**
     * Gets all friends of a player along with the timestamps of when the friendships were created.
     *
     * @param player UUID of the player to get friends for
     * @return List of pairs containing friend UUIDs and friendship creation timestamps (in milliseconds since epoch)
     */
    fun getFriends(player: UUID): List<Pair<UUID, Long>> {
        try {
            val playerUuid = player.shorten()
            connection
                .prepareStatement(
                    """
                SELECT player1_uuid, player2_uuid, friendship_date 
                FROM friendships 
                WHERE player1_uuid = ? OR player2_uuid = ?
                """,
                ).use { stmt ->
                    stmt.setString(1, playerUuid)
                    stmt.setString(2, playerUuid)

                    stmt.executeQuery().use { rs ->
                        val friends = mutableListOf<Pair<UUID, Long>>()
                        while (rs.next()) {
                            val uuid1 = rs.getString("player1_uuid")
                            val uuid2 = rs.getString("player2_uuid")
                            val timestamp = rs.getTimestamp("friendship_date").time
                            // Add the UUID that isn't the player's UUID
                            friends.add(
                                if (uuid1 == playerUuid) {
                                    uuid2.expandToUUID() to timestamp
                                } else {
                                    uuid1.expandToUUID() to timestamp
                                },
                            )
                        }
                        return friends
                    }
                }
        } catch (e: SQLException) {
            println("Failed to get friends: ${e.message}")
            e.printStackTrace()
            return emptyList()
        }
    }

    /**
     * Finds all mutual friends between two players.
     * A mutual friend is someone who is friends with both input players.
     *
     * @param player1 UUID of the first player
     * @param player2 UUID of the second player
     * @return List of UUIDs representing mutual friends
     */
    fun getMutualFriends(
        player1: UUID,
        player2: UUID,
    ): List<UUID> {
        try {
            val uuid1 = player1.shorten()
            val uuid2 = player2.shorten()

            connection
                .prepareStatement(
                    """
            SELECT f1.player1_uuid, f1.player2_uuid
            FROM friendships f1
            INNER JOIN friendships f2
            WHERE 
                -- Find friends of player1
                (f1.player1_uuid = ? OR f1.player2_uuid = ?)
                AND
                -- Find friends of player2
                (f2.player1_uuid = ? OR f2.player2_uuid = ?)
                AND
                -- Match on the common friend
                (
                    (f1.player1_uuid = f2.player1_uuid AND f1.player1_uuid NOT IN (?, ?))
                    OR
                    (f1.player1_uuid = f2.player2_uuid AND f1.player1_uuid NOT IN (?, ?))
                    OR
                    (f1.player2_uuid = f2.player1_uuid AND f1.player2_uuid NOT IN (?, ?))
                    OR
                    (f1.player2_uuid = f2.player2_uuid AND f1.player2_uuid NOT IN (?, ?))
                )
            """,
                ).use { stmt ->
                    // Set parameters for player1
                    stmt.setString(1, uuid1)
                    stmt.setString(2, uuid1)
                    // Set parameters for player2
                    stmt.setString(3, uuid2)
                    stmt.setString(4, uuid2)
                    // Set exclusion parameters (exclude the original players)
                    stmt.setString(5, uuid1)
                    stmt.setString(6, uuid2)
                    stmt.setString(7, uuid1)
                    stmt.setString(8, uuid2)
                    stmt.setString(9, uuid1)
                    stmt.setString(10, uuid2)
                    stmt.setString(11, uuid1)
                    stmt.setString(12, uuid2)

                    stmt.executeQuery().use { rs ->
                        val mutualFriends = mutableSetOf<UUID>()
                        while (rs.next()) {
                            val friendUuid1 = rs.getString("player1_uuid")
                            val friendUuid2 = rs.getString("player2_uuid")
                            // Add the UUID that isn't either of the input players
                            when {
                                friendUuid1 != uuid1 && friendUuid1 != uuid2 ->
                                    mutualFriends.add(friendUuid1.expandToUUID())
                                friendUuid2 != uuid1 && friendUuid2 != uuid2 ->
                                    mutualFriends.add(friendUuid2.expandToUUID())
                            }
                        }
                        return mutualFriends.toList()
                    }
                }
        } catch (e: SQLException) {
            println("Failed to get mutual friends: ${e.message}")
            e.printStackTrace()
            return emptyList()
        }
    }

    /**
     * Removes a friendship between two players if it exists.
     *
     * @param player1 UUID of the first player
     * @param player2 UUID of the second player
     * @return true if friendship was removed successfully, false if it didn't exist or there was an error
     */
    fun removeFriendship(
        player1: UUID,
        player2: UUID,
    ): Boolean {
        try {
            val uuid1 = player1.shorten()
            val uuid2 = player2.shorten()

            // Since we store with smaller UUID first, we need to check both orderings
            connection
                .prepareStatement(
                    """
            DELETE FROM friendships 
            WHERE (player1_uuid = ? AND player2_uuid = ?) 
               OR (player1_uuid = ? AND player2_uuid = ?)
            """,
                ).use { stmt ->
                    stmt.setString(1, uuid1)
                    stmt.setString(2, uuid2)
                    stmt.setString(3, uuid2)
                    stmt.setString(4, uuid1)
                    return stmt.executeUpdate() > 0
                }
        } catch (e: SQLException) {
            println("Failed to remove friendship: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Checks if two players are friends.
     *
     * @param player1 UUID of the first player
     * @param player2 UUID of the second player
     * @return true if the players are friends, false otherwise
     */
    fun areFriends(
        player1: UUID,
        player2: UUID,
    ): Boolean {
        try {
            val uuid1 = player1.shorten()
            val uuid2 = player2.shorten()

            connection
                .prepareStatement(
                    """
            SELECT 1 FROM friendships 
            WHERE (player1_uuid = ? AND player2_uuid = ?) 
               OR (player1_uuid = ? AND player2_uuid = ?)
            """,
                ).use { stmt ->
                    stmt.setString(1, uuid1)
                    stmt.setString(2, uuid2)
                    stmt.setString(3, uuid2)
                    stmt.setString(4, uuid1)
                    return stmt.executeQuery().next()
                }
        } catch (e: SQLException) {
            println("Failed to check friendship: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Gets the total number of friends a player has.
     *
     * @param player UUID of the player to check
     * @return number of friends the player has
     */
    fun getFriendCount(player: UUID): Int {
        try {
            val uuid = player.shorten()
            connection
                .prepareStatement(
                    """
            SELECT COUNT(*) as count FROM friendships 
            WHERE player1_uuid = ? OR player2_uuid = ?
            """,
                ).use { stmt ->
                    stmt.setString(1, uuid)
                    stmt.setString(2, uuid)
                    stmt.executeQuery().use { rs ->
                        return if (rs.next()) rs.getInt("count") else 0
                    }
                }
        } catch (e: SQLException) {
            println("Failed to get friend count: ${e.message}")
            e.printStackTrace()
            return 0
        }
    }

    fun writeNames(names: Map<UUID, String>) {
        try {
            // insert new names (on duplicate key update)
            val updateStatement =
                connection.prepareStatement(
                    "INSERT INTO names (uuid, name) VALUES (?, ?) ON DUPLICATE KEY UPDATE name = ?",
                )
            for ((uuid, name) in names) {
                updateStatement.setString(1, uuid.shorten())
                updateStatement.setString(2, name)
                updateStatement.setString(3, name)
                updateStatement.executeUpdate()
            }
            updateStatement.close()
        } catch (e: SQLException) {
            println("Failed to write names: ${e.message}")
            e.printStackTrace()
        }
    }

    fun fetchName(uuid: UUID): String? {
        try {
            connection.prepareStatement("SELECT name FROM names WHERE uuid = ?").use { statement ->
                statement.setString(1, uuid.shorten())
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        return rs.getString("name")
                    }
                }
            }
        } catch (e: SQLException) {
            println("Failed to fetch name: ${e.message}")
            e.printStackTrace()
        }
        return null
    }

    /**
     * Closes the database connection.
     */
    fun close() {
        try {
            connection.close()
        } catch (e: SQLException) {
            println("Failed to close database connection: ${e.message}")
        }
    }

    fun fetchUUID(name: String): UUID? {
        try {
            val statement = connection.prepareStatement("SELECT uuid FROM names WHERE name = ?")
            statement.setString(1, name)
            statement.executeQuery().use { rs ->
                if (rs.next()) {
                    return rs.getString("uuid").expandToUUID()
                }
            }
        } catch (e: SQLException) {
            println("Failed to fetch UUID: ${e.message}")
            e.printStackTrace()
        }
        return null
    }
}
