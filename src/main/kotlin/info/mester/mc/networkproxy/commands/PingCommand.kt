package info.mester.mc.networkproxy.commands

import com.mojang.brigadier.Command
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage

class PingCommand {
    companion object {
        fun createCommand(): BrigadierCommand {
            val node =
                BrigadierCommand
                    .literalArgumentBuilder("ping")
                    .executes { context ->
                        val source = context.source
                        if (source !is Player) {
                            source.sendMessage(
                                Component.text(
                                    "This command can only be run by a player",
                                    NamedTextColor.RED,
                                ),
                            )
                            return@executes 1
                        }
                        source.sendMessage(MiniMessage.miniMessage().deserialize("<green>Your ping is <yellow>${source.ping}ms"))
                        Command.SINGLE_SUCCESS
                    }.build()
            return BrigadierCommand(node)
        }
    }
}
