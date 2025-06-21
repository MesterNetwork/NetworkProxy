package info.mester.network.plugin.ui

import info.mester.network.plugin.NetworkPlugin
import info.mester.network.plugin.createBasicItem
import info.mester.network.plugin.mm
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.luckperms.api.node.Node
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.InventoryHolder
import org.bukkit.persistence.PersistentDataType

private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()

data class RegisteredTag(
    val name: String,
    val suffix: String,
) {
    val component = legacySerializer.deserialize(suffix.substring(1))
}

val borderItem =
    createBasicItem(Material.BLACK_STAINED_GLASS_PANE, "", 1).apply {
        editMeta { meta ->
            meta.isHideTooltip = true
        }
    }

private val nextPage = createBasicItem(Material.LIME_DYE, "<green>Next Page", 1)
private val previousPage = createBasicItem(Material.RED_DYE, "<red>Previous Page", 1)
private val clearTags = createBasicItem(Material.BARRIER, "<red>Clear Tags", 1)

class TagsUI(
    private val tags: List<RegisteredTag>,
    private val player: Player,
) : InventoryHolder {
    private val plugin = NetworkPlugin.instance
    private val luckPerms = plugin.luckPerms
    private val inventory = Bukkit.createInventory(this, 4 * 9, Component.text("Tags", NamedTextColor.DARK_GRAY))
    private var page = 0
    private val playerPrefix = luckPerms.getPlayerAdapter(Player::class.java).getMetaData(player).prefix ?: ""

    private fun getPlayerPreview(tag: RegisteredTag): Component {
        val finalText = playerPrefix + player.name + tag.suffix
        return legacySerializer.deserialize(finalText)
    }

    private fun reload() {
        inventory.clear()
        for (i in 0 until inventory.size) {
            inventory.setItem(i, borderItem)
        }
        // one page contains 14 tags
        for (i in 0 until 14) {
            // we use the 2nd and 3rd rows without the first and last items
            val inventoryIndex = 10 + (i / 7) * 9 + (i % 7)
            val tagIndex = page * 14 + i
            if (tagIndex >= tags.size) {
                // replace with an empty gray glass
                inventory.setItem(inventoryIndex, borderItem.withType(Material.GRAY_STAINED_GLASS_PANE))
                continue
            }
            val tag = tags[tagIndex]
            val tagItem =
                createBasicItem(
                    Material.NAME_TAG,
                    mm.serialize(tag.component),
                    1,
                    mm.serialize(getPlayerPreview(tag)),
                    "",
                    "<!i><gray>Click to apply this tag.",
                ).apply {
                    editMeta { meta ->
                        meta.persistentDataContainer.set(NamespacedKey(plugin, "tag"), PersistentDataType.STRING, tag.name)
                    }
                }
            inventory.setItem(inventoryIndex, tagItem)
        }
        // check if we need a next page button
        if (page * 14 + 14 < tags.size) {
            inventory.setItem(32, nextPage)
        }
        // always show the previous page button unless we're on the first page
        if (page > 0) {
            inventory.setItem(30, previousPage)
        }
        // add the clear tags button
        inventory.setItem(31, clearTags)
    }

    init {
        reload()
    }

    override fun getInventory() = inventory

    fun handleClick(event: InventoryClickEvent) {
        val slot = event.slot
        val tagsTrack = luckPerms.trackManager.getTrack("tags")!!
        if (slot == 31) {
            // remove all tags from the player
            luckPerms.userManager.modifyUser(player.uniqueId) { user ->
                user.data().clear { node ->
                    // the node key is made up of "group." and the tag name
                    // remove the "group." part to get the tag name
                    val groupKey = node.key.removePrefix("group.")
                    tagsTrack.containsGroup(groupKey)
                }
            }
            player.sendMessage(
                Component.text(
                    "Successfully cleared tags. Please rejoin the server for the changes to fully take effect.",
                    NamedTextColor.GREEN,
                ),
            )
            player.closeInventory()
            return
        }
        val item = event.currentItem
        // this condition will only be true if the player didn't click on a border item
        if (item?.type != borderItem.type) {
            // check if we clicked on the next page button or previous page button
            if (slot == 30) {
                page--
                reload()
                return
            }
            if (slot == 32) {
                page++
                reload()
                return
            }
        }
        // now check for the tag
        val tag = event.currentItem?.persistentDataContainer?.get(NamespacedKey(plugin, "tag"), PersistentDataType.STRING) ?: return
        // apply the tag to the player
        luckPerms.userManager.modifyUser(player.uniqueId) { user ->
            // clear the user's tags track
            user.data().clear { node ->
                // the node key is made up of "group." and the tag name
                // remove the "group." part to get the tag name
                val groupKey = node.key.removePrefix("group.")
                tagsTrack.containsGroup(groupKey)
            }
            // add the tag to the user's tags track
            user.data().add(Node.builder("group.$tag").build())
        }
        player.sendMessage(
            Component.text(
                "Successfully changed tags. Please rejoin the server for the changes to fully take effect.",
                NamedTextColor.GREEN,
            ),
        )
        player.closeInventory()
    }
}
