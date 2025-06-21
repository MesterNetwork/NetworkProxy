package info.mester.network.proxy.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.proxy.Player
import info.mester.network.proxy.NetworkProxy

data class MessageSession(
    val player1: Player,
    val player2: Player,
    val lastActivity: Long,
)

class MessageCommand(
    private val plugin: NetworkProxy,
) {
    companion object {
        private val messageNode =
            BrigadierCommand
                .literalArgumentBuilder("message")
                .then(
                    BrigadierCommand
                        .requiredArgumentBuilder(
                            "message",
                            StringArgumentType.greedyString(),
                        ),
                )
    }

    private val proxy = plugin.proxy
    private val luckPerms = plugin.luckPerms

    private val sessions = mutableListOf<MessageSession>()

    fun getCommand(): BrigadierCommand {
        val playerNode =
            BrigadierCommand
                .requiredArgumentBuilder(
                    "player",
                    StringArgumentType.word(),
                ).suggests { ctx, builder ->
                    val source = ctx.source
                    if (source !is Player) {
                        return@suggests builder.buildFuture()
                    }
                    val players =
                        proxy.allPlayers
                            .filter { it.uniqueId != source.uniqueId }
                            .map { it.username }
                            .sortedBy { it.lowercase() }
                    runCatching {
                        val current = StringArgumentType.getString(ctx, "player")
                        players.filter { it.startsWith(current) }.forEach { builder.suggest(it) }
                    }.onFailure {
                        players.forEach { builder.suggest(it) }
                    }
                    builder.buildFuture()
                }

        val node =
            BrigadierCommand
                .literalArgumentBuilder("message")
                .then(
                    playerNode.then(
                        messageNode
                            .executes { ctx ->
                                val source = ctx.source
                                if (source !is Player) {
                                    return@executes 1
                                }
                                val targetName = StringArgumentType.getString(ctx, "player")
                                val target = proxy.getPlayer(targetName)
                                if (target.isEmpty) {
                                    source.sendMessage(plugin.getMessage("general.player_not_found", targetName))
                                    return@executes 1
                                }
                                if (target.get().uniqueId == source.uniqueId) {
                                    source.sendMessage(plugin.getMessage("general.cannot_message_self"))
                                    return@executes 1
                                }
                                // TODO: implement message logic
                                Command.SINGLE_SUCCESS
                            },
                    ),
                ).build()
        return BrigadierCommand(node)
    }
}
