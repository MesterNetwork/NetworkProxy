package info.mester.mc.networkproxy.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import java.net.InetSocketAddress

class ProxyCommand {
    companion object {
        fun createCommand(): BrigadierCommand {
            val node =
                BrigadierCommand
                    .literalArgumentBuilder("proxy")
                    .requires { it.hasPermission("networkproxy.proxy") }
                    .then(
                        BrigadierCommand
                            .requiredArgumentBuilder("proxy", StringArgumentType.word())
                            .suggests { _, builder ->
                                builder.suggest("na")
                                builder.suggest("eu")
                                builder.buildFuture()
                            }.executes { context ->
                                val source = context.source
                                if (source !is Player) {
                                    source.sendMessage(Component.text("This command can only be run by a player", NamedTextColor.RED))
                                    return@executes 1
                                }
                                val currentServer = source.currentServer
                                if (currentServer.isEmpty) {
                                    source.sendMessage(Component.text("You are not connected to a server", NamedTextColor.RED))
                                    return@executes 1
                                }
                                val proxyString =
                                    StringArgumentType
                                        .getString(context, "proxy")
                                        .lowercase()
                                if (proxyString == "na") {
                                    // send player to na.mester.info
                                    source.transferToHost(InetSocketAddress("na.mester.info", 25565))
                                    return@executes 1
                                }
                                if (proxyString == "eu") {
                                    // send player to play.mester.info
                                    source.transferToHost(InetSocketAddress("play.mester.info", 25565))
                                    return@executes 1
                                }
                                source.sendMessage(Component.text("Invalid proxy name", NamedTextColor.RED))
                                Command.SINGLE_SUCCESS
                            },
                    ).build()
            return BrigadierCommand(node)
        }
    }
}
