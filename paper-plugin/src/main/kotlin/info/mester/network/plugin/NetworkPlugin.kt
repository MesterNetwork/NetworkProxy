package info.mester.network.plugin

import info.mester.network.plugin.commands.TagsCommand
import info.mester.network.plugin.events.ChatEmoji
import info.mester.network.plugin.events.CustomUI
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.luckperms.api.LuckPerms
import net.luckperms.api.LuckPermsProvider
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

val mm = MiniMessage.miniMessage()

fun createBasicItem(
    material: Material,
    name: Component,
    amount: Int,
    vararg lore: Component,
): ItemStack {
    val item = ItemStack.of(material, amount)
    item.editMeta { meta ->
        meta.displayName(name)
        meta.lore(lore.toList())
    }
    return item
}

fun createBasicItem(
    material: Material,
    name: String,
    amount: Int,
    vararg lore: String,
): ItemStack = createBasicItem(material, mm.deserialize("<!i>$name"), amount, *lore.map { mm.deserialize("<!i>$it") }.toTypedArray())

class NetworkPlugin : JavaPlugin() {
    lateinit var database: DatabaseReader
    lateinit var luckPerms: LuckPerms

    companion object {
        lateinit var instance: NetworkPlugin
    }

    @Suppress("UnstableApiUsage")
    override fun onEnable() {
        instance = this
        database = DatabaseReader
        luckPerms = LuckPermsProvider.get()
        // set up commands
        val manager = lifecycleManager
        manager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val commands = event.registrar()
            commands.register(
                TagsCommand.getCommand(),
                "Open the tags selection UI",
            )
        }
        server.pluginManager.registerEvents(ChatEmoji(this), this)
        server.pluginManager.registerEvents(CustomUI(), this)
    }
}
