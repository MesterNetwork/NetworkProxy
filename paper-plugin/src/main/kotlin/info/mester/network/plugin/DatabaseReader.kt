package info.mester.network.plugin

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.SQLException
import java.util.logging.Level

object DatabaseReader {
    private val logger = NetworkPlugin.instance.logger
    private val dataSource: HikariDataSource
    private val connection: Connection

    init {
        val config = HikariConfig()
        config.jdbcUrl = "jdbc:mysql://localhost:3306/mesternetwork?useSSL=false"
        config.username = "server"
        config.password = "serverpassword"
        config.driverClassName = "com.mysql.cj.jdbc.Driver"
        dataSource = HikariDataSource(config)
        connection = dataSource.connection
    }

    fun fetchEmojis(): List<Emoji> =
        try {
            connection.prepareStatement("SELECT * FROM emojis")?.use { statement ->
                val resultSet = statement.executeQuery()
                val emojis = mutableListOf<Emoji>()
                while (resultSet.next()) {
                    val keyword = resultSet.getString("keyword")
                    val emoji = resultSet.getString("emoji")
                    emojis.add(Emoji(keyword, emoji))
                }
                emojis
            } ?: emptyList()
        } catch (e: SQLException) {
            logger.log(Level.SEVERE, "Failed to fetch emojis from database", e)
            emptyList()
        }
}
