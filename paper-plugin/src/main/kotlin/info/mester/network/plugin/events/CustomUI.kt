package info.mester.network.plugin.events

import info.mester.network.plugin.ui.TagsUI
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent

class CustomUI : Listener {
    @EventHandler
    fun playerClicksInventory(event: InventoryClickEvent) {
        val holder = event.inventory.holder
        if (holder is TagsUI) {
            event.isCancelled = true
            holder.handleClick(event)
        }
    }
}
