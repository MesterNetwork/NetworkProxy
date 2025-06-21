package info.mester.network.proxy

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PluginMessageEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Dependency
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import de.exlll.configlib.YamlConfigurationProperties
import de.exlll.configlib.YamlConfigurations
import info.mester.network.proxy.commands.HubCommand
import info.mester.network.proxy.commands.NetworkCommand
import info.mester.network.proxy.commands.PingCommand
import info.mester.network.proxy.commands.ReportCommand
import io.lettuce.core.RedisURI
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.luckperms.api.LuckPerms
import net.luckperms.api.LuckPermsProvider
import org.slf4j.Logger
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path

@Suppress("unused")
@Plugin(
    id = "networkproxy",
    name = "networkproxy",
    version = "1.0-SNAPSHOT",
    description = "The Velocity proxy plugin for Mester Network",
    url = "https://github.com/MesterNetwork/NetworkProxy",
    authors = ["Mester"],
    dependencies = [
        Dependency(id = "luckperms"),
    ],
)
class NetworkProxy
    @Inject
    constructor(
        val proxy: ProxyServer,
        val logger: Logger,
        @DataDirectory
        val dataDirectory: Path,
    ) {
        private val yamlLoader = YamlConfigurationProperties.newBuilder().charset(Charset.forName("UTF-8")).build()
        private val configPath = dataDirectory.resolve("config.yml")
        private val messagesPath = dataDirectory.resolve("messages.yml")
        var config: PluginConfig = PluginConfig()
            private set
        var messages: MessagesConfig = MessagesConfig()
            private set

        lateinit var luckPerms: LuckPerms
            private set
        lateinit var nameCache: NameCache
            private set

        val database = DatabaseManager

        fun loadConfig(replace: Boolean = false) {
            try {
                // ensure data directory exists
                if (!Files.exists(dataDirectory)) {
                    Files.createDirectories(dataDirectory)
                }

                val commandsPath = dataDirectory.resolve("commands")
                if (!Files.exists(commandsPath)) {
                    Files.createDirectories(commandsPath)
                }

                // read default config
                if (replace) {
                    copyDefaultConfig("config.yml", configPath.toFile())
                    copyDefaultConfig("messages.yml", messagesPath.toFile())
                }

                config = YamlConfigurations.load(configPath, PluginConfig::class.java, yamlLoader)
                messages = YamlConfigurations.load(messagesPath, MessagesConfig::class.java, yamlLoader)
            } catch (e: IOException) {
                logger.error("Failed to load configuration", e)
                throw IllegalStateException("Failed to load configuration", e)
            }
        }

        @Throws(IllegalStateException::class)
        private fun copyDefaultConfig(
            resourcePath: String,
            configFile: File,
        ) {
            javaClass.classLoader.getResourceAsStream(resourcePath).use { inputStream ->
                if (inputStream == null) {
                    logger.error("Failed to find default config")
                    throw IllegalStateException("Failed to find default config")
                }

                configFile
                    .outputStream()
                    .use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
            }
        }

        @Subscribe
        fun onProxyInitialization(event: ProxyInitializeEvent) {
            // load config
            loadConfig(true)

            luckPerms = LuckPermsProvider.get()
            nameCache = NameCache(this)

            // set up redis and party manager
            val redisURI = RedisURI.create("localhost", 6379)

            // register commands
            val commandManager = proxy.commandManager
            // register /ping command
            val pingCommand = PingCommand.createCommand()
            val pingMeta =
                commandManager
                    .metaBuilder(pingCommand)
                    .plugin(this)
                    .build()
            // register /hub command
            val hubServer = proxy.getServer("hub")
            if (hubServer.isEmpty) {
                logger.error("Failed to find hub server, cancelling /hub command registration")
                return
            } else {
                val hubCommand = HubCommand.createCommand(hubServer.get())
                val hubMeta =
                    commandManager
                        .metaBuilder(hubCommand)
                        .plugin(this)
                        .build()
                commandManager.register(hubMeta, hubCommand)
            }
            // register /report command
            val reportCommand = ReportCommand.createCommand(proxy)
            val reportMeta =
                commandManager
                    .metaBuilder(reportCommand)
                    .plugin(this)
                    .build()
            // create /network command
            val networkCommand = NetworkCommand(this).getCommand()
            val emojiMeta =
                commandManager
                    .metaBuilder(networkCommand)
                    .plugin(this)
                    .build()
            // register commands
            commandManager.register(pingMeta, pingCommand)
            commandManager.register(reportMeta, reportCommand)
            commandManager.register(emojiMeta, networkCommand)

            // write emojis to database
            database.writeEmojis(config.emojis)
        }

        @Subscribe
        fun onPluginMessage(event: PluginMessageEvent) {
            logger.info("Received plugin message on channel ${event.identifier} (${event.data.size} bytes)")
        }

        @Subscribe
        fun onProxyShutdown(event: ProxyShutdownEvent) {
            database.close()
        }

        fun getMessage(
            key: String,
            vararg substitutions: String,
        ): Component {
            val group = key.substringBefore('.')
            val message =
                when (group) {
                    "general" -> messages.general[key.substringAfter('.')]
                    "friends" -> messages.friends[key.substringAfter('.')]
                    else -> null
                }
            if (message == null) {
                logger.error("Failed to find message for key $key")
                return Component.text("#error: message not found#", NamedTextColor.RED)
            }
            // count how many `%s` there are
            val subsRegex = "%s".toRegex()
            val subsCount = subsRegex.findAll(message).count()
            if (subsCount != substitutions.size) {
                logger.error("Substitution count mismatch for key $key")
                return Component.text(
                    "#error: invalid substitution count, got ${substitutions.size} expected $subsCount#",
                    NamedTextColor.RED,
                )
            }
            // replace `%s` with substitutions
            var result = message ?: ""
            val iterator = substitutions.iterator()
            while ("%s" in result && iterator.hasNext()) {
                result = result.replaceFirst("%s", iterator.next())
            }
            return MiniMessage.miniMessage().deserialize(result)
        }
    }
