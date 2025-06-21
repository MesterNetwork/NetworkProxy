package info.mester.network.proxy

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.ServerConnectedEvent
import java.util.UUID
import java.util.concurrent.TimeUnit

class NameCache(
    plugin: NetworkProxy,
) {
    private val database = plugin.database
    private val proxy = plugin.proxy
    private val logger = plugin.logger

    private val cache = mutableMapOf<UUID, String>()

    init {
        // create a 5-minute timer that flushes the cache
        plugin.proxy.scheduler
            .buildTask(
                plugin,
                Runnable {
                    logger.info("Flushing name cache")
                    database.writeNames(cache.toMap())
                    cache.clear()
                },
            ).delay(5, TimeUnit.MINUTES)
            .repeat(5, TimeUnit.MINUTES)
            .schedule()
        plugin.proxy.eventManager.register(plugin, this)
    }

    fun getName(uuid: UUID): String? {
        if (cache.containsKey(uuid)) {
            return cache[uuid]
        }
        val name = database.fetchName(uuid) ?: return null
        cache[uuid] = name
        return name
    }

    fun getUUID(name: String): UUID? {
        // step 1: check if the player is online
        val player = proxy.getPlayer(name)
        if (player.isPresent) {
            return player.get().uniqueId
        }
        // step 2: check if the player is in the cache
        if (cache.containsValue(name)) {
            return cache.entries.firstOrNull { it.value == name }?.key
        }
        // step 3: check if the player is in the database
        val uuid = database.fetchUUID(name) ?: return null
        cache[uuid] = name
        return uuid
    }

    @Subscribe
    fun onPlayerJoin(event: ServerConnectedEvent) {
        val player = event.player
        val uuid = player.uniqueId
        cache[uuid] = player.username
    }
}
