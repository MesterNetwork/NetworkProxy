package info.mester.network.plugin.events

import info.mester.network.plugin.Emoji
import info.mester.network.plugin.NetworkPlugin
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class ChatEmoji(
    private val plugin: NetworkPlugin,
) : Listener {
    private val emojis = mutableListOf<Emoji>()
    private val mm = MiniMessage.miniMessage()
    private val logger = plugin.logger

    @EventHandler
    fun playerJoins(event: PlayerJoinEvent) {
        // reload the emojis
        logger.info("Reloading emojis")
        Bukkit.getAsyncScheduler().runNow(plugin) {
            val emojis = plugin.database.fetchEmojis()
            if (emojis.isNotEmpty()) {
                logger.info("Got emojis: ${emojis.joinToString { it.keyword }}")
                this.emojis.clear()
                this.emojis.addAll(emojis)
            }
        }
    }

    @EventHandler
    fun playerSendsChatMessage(event: AsyncChatEvent) {
        // check if player has beginner role
        if (!event.player.hasPermission("group.beginner")) {
            return
        }
        if (emojis.isEmpty()) {
            return
        }
        var message = event.originalMessage()
        // iteratively go through each emoji and look for the keyword
        for (emoji in emojis) {
            val keyword = ":${emoji.keyword}:"
            message =
                message.replaceText { builder ->
                    builder.matchLiteral(keyword).replacement(mm.deserialize(emoji.emoji))
                }
        }
        // check if the message was actually changed
        if (message == event.originalMessage()) {
            return
        }
        event.message(message)
    }
}
