package info.mester.network.proxy.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage

class ReportCommand {
    companion object {
        fun createCommand(proxyServer: ProxyServer): BrigadierCommand {
            val node =
                BrigadierCommand
                    .literalArgumentBuilder("report")
                    .then(
                        BrigadierCommand
                            .requiredArgumentBuilder("player", StringArgumentType.word())
                            .suggests { _, builder ->
                                val players = proxyServer.allPlayers.map { it.username }
                                players.forEach { builder.suggest(it) }
                                builder.buildFuture()
                            }.then(
                                BrigadierCommand
                                    .requiredArgumentBuilder("reason", StringArgumentType.greedyString())
                                    .executes { context ->
                                        val source = context.source
                                        if (source !is Player) {
                                            source.sendMessage(
                                                Component.text("This command can only be run by a player", NamedTextColor.RED),
                                            )
                                            return@executes 1
                                        }
                                        // get player
                                        val playerName = StringArgumentType.getString(context, "player")
                                        val reason = StringArgumentType.getString(context, "reason")
                                        // find player
                                        val player =
                                            runCatching {
                                                proxyServer.getPlayer(playerName).get()
                                            }.getOrNull()
                                        if (player == null) {
                                            source.sendMessage(
                                                Component.text(
                                                    "Player is not online. Please join our Discord server with proof to report them!",
                                                    NamedTextColor.RED,
                                                ),
                                            )
                                            return@executes 1
                                        }
                                        // if the reported player is the same as the reporter or the reporter is a moderator, fail
                                        if (player.uniqueId == source.uniqueId || player.hasPermission("group.moderator")) {
                                            source.sendMessage(
                                                Component.text(
                                                    "You cannot report yourself or a moderator!",
                                                    NamedTextColor.RED,
                                                ),
                                            )
                                            return@executes 1
                                        }
                                        // get all players with permission "group.moderator"
                                        val moderators = proxyServer.allPlayers.filter { it.hasPermission("group.moderator") }
                                        moderators.forEach { moderator ->
                                            moderator.sendMessage(
                                                MiniMessage.miniMessage().deserialize(
                                                    "<aqua>Player <yellow>${player.username}</yellow> has been reported by <yellow>${source.username}</yellow> for reason: <red><bold>$reason",
                                                ),
                                            )
                                        }
                                        Command.SINGLE_SUCCESS
                                    },
                            ),
                    ).build()
            return BrigadierCommand(node)
        }
    }
}
