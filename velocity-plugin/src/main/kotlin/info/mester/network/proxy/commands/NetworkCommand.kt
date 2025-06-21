package info.mester.network.proxy.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandSource
import info.mester.network.proxy.NetworkProxy
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

class NetworkCommand(
    private val plugin: NetworkProxy,
) {
    private fun reloadConfig(ctx: CommandContext<CommandSource>): Int {
        // Reload the config
        plugin.loadConfig(false)
        plugin.database.writeEmojis(plugin.config.emojis)
        ctx.source.sendMessage(
            Component.text("Successfully reloaded config", NamedTextColor.GREEN),
        )
        return Command.SINGLE_SUCCESS
    }

    fun getCommand(): BrigadierCommand {
        val node =
            BrigadierCommand
                .literalArgumentBuilder("network")
                .requires { it.hasPermission("networkproxy.network") }
                .then(
                    BrigadierCommand
                        .literalArgumentBuilder("reload")
                        .executes { ctx ->
                            reloadConfig(ctx)
                        },
                ).build()
        return BrigadierCommand(node)
    }
}
