package info.mester.network.plugin.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import info.mester.network.plugin.NetworkPlugin
import info.mester.network.plugin.mm
import info.mester.network.plugin.ui.RegisteredTag
import info.mester.network.plugin.ui.TagsUI
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.entity.Player
import java.util.logging.Level

@Suppress("UnstableApiUsage")
object TagsCommand {
    fun getCommand(): LiteralCommandNode<CommandSourceStack> {
        val plugin = NetworkPlugin.instance
        val luckPerms = plugin.luckPerms
        val logger = plugin.logger
        return Commands
            .literal("tags")
            .executes { context ->
                val source = context.source.sender
                if (source !is Player) {
                    source.sendMessage(mm.deserialize("<red>You have to be a player to run this command!"))
                    return@executes 1
                }
                // load all tags
                val tagsTrack = luckPerms.trackManager.getTrack("tags")
                if (tagsTrack == null) {
                    logger.log(Level.SEVERE, "Failed to find tags track")
                    throw IllegalStateException("Failed to find tags track")
                }
                val tagNames = tagsTrack.groups.toList()
                val tags =
                    tagNames
                        .mapNotNull { tagName ->
                            val group = luckPerms.groupManager.getGroup(tagName)
                            if (group == null) {
                                logger.log(Level.SEVERE, "Failed to find group $tagName")
                                return@mapNotNull null
                            }
                            // the preview is the group's suffix
                            val suffix = group.cachedData.metaData.suffix
                            if (suffix == null) {
                                logger.log(Level.SEVERE, "Failed to find suffix for group $tagName")
                                return@mapNotNull null
                            }
                            RegisteredTag(tagName, suffix)
                        }.sortedBy { it.name }
                val ui = TagsUI(tags, source)
                source.openInventory(ui.getInventory())
                Command.SINGLE_SUCCESS
            }.build()
    }
}
