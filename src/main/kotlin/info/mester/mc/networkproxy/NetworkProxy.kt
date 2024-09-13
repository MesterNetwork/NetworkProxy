package info.mester.mc.networkproxy

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import info.mester.mc.networkproxy.commands.HubCommand
import info.mester.mc.networkproxy.commands.PingCommand
import info.mester.mc.networkproxy.commands.ProxyCommand
import info.mester.mc.networkproxy.commands.ReportCommand
import org.slf4j.Logger

@Suppress("unused")
@Plugin(
    id = "networkproxy",
    name = "networkproxy",
    version = BuildConstants.VERSION,
    description = "The Velocity proxy plugin for Mester Network",
    url = "https://mc.mester.info",
    authors = ["Mester"],
)
class NetworkProxy
    @Inject
    constructor(
        private val proxy: ProxyServer,
        val logger: Logger,
    ) {
        @Subscribe
        fun onProxyInitialization(event: ProxyInitializeEvent) {
            val commandManager = proxy.commandManager
            // register /proxy command
            val proxyMeta =
                commandManager
                    .metaBuilder("proxy")
                    .plugin(this)
                    .build()
            val proxyCommand = ProxyCommand.createCommand()
            // register /ping command
            val pingMeta =
                commandManager
                    .metaBuilder("ping")
                    .plugin(this)
                    .build()
            val pingCommand = PingCommand.createCommand()
            // register /hub command
            val hubMeta =
                commandManager
                    .metaBuilder("hub")
                    .plugin(this)
                    .build()
            val hubServer = proxy.getServer("hub")
            if (hubServer.isEmpty) {
                logger.error("Failed to find hub server")
                return
            }
            val hubCommand = HubCommand.createCommand(hubServer.get())
            // register /report command
            val reportMeta =
                commandManager
                    .metaBuilder("report")
                    .plugin(this)
                    .build()
            val reportCommand = ReportCommand.createCommand(proxy)
            // register commands
            commandManager.register(proxyMeta, proxyCommand)
            commandManager.register(pingMeta, pingCommand)
            commandManager.register(hubMeta, hubCommand)
            commandManager.register(reportMeta, reportCommand)
        }
    }
