package info.mester.mc.networkproxy.commands

import com.mojang.brigadier.Command
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.server.RegisteredServer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

class HubCommand {
    companion object {
        fun createCommand(hubServer: RegisteredServer): BrigadierCommand {
            val node =
                BrigadierCommand
                    .literalArgumentBuilder("hub")
                    .requires { it.hasPermission("networkproxy.lobby") }
                    .executes { context ->
                        val source = context.source
                        if (source !is Player) {
                            source.sendMessage(
                                Component.text("This command can only be run by a player", NamedTextColor.RED),
                            )
                            return@executes 1
                        }
                        source.createConnectionRequest(hubServer).connect().thenAccept { result ->
                            if (!result.isSuccessful) {
                                source.sendMessage(Component.text("Failed to connect to hub server", NamedTextColor.RED))
                            }
                        }
                        Command.SINGLE_SUCCESS
                    }.build()
            return BrigadierCommand(node)
        }
    }
}
