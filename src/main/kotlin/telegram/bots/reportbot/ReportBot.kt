package telegram.bots.reportbot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.HandleUpdate
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.stickers.ChatPermissions
import com.github.kotlintelegrambot.extensions.filters.Filter
import org.jetbrains.exposed.sql.Database
import java.util.logging.Logger

class ReportBot(
    val token: String,
    val db: Database,
    private val logger: Logger = Logger.getLogger("ReportBot")
) {

    private val bot: Bot = bot {
        token = this@ReportBot.token

        dispatch {
            message(Filter.All) { bot, update ->

            }
        }
    }

    fun run() = bot.startPolling()
}