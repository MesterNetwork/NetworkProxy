package info.mester.network.proxy

import de.exlll.configlib.Configuration

@Configuration
class EmojiConfig {
    var keyword: String = ""
    var emoji: String = ""
}

@Configuration
class PluginConfig {
    var emojis: List<EmojiConfig> = emptyList()
    var socialSpyWebhook: String = ""
}

@Configuration
class MessagesConfig {
    var general = emptyMap<String, String>()
    var friends = emptyMap<String, String>()
}
