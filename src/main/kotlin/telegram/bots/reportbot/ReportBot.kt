package telegram.bots.reportbot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.*
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
                val message = update.message ?: return@message
                val chatId = message.chat.id
                bot.sendMessage(chatId, "message", replyToMessageId = message.messageId)
            }

            sticker { bot, update, sticker ->
                val message = update.message ?: return@sticker
                val chatId = message.chat.id
                bot.sendMessage(chatId, "sticker", replyToMessageId = message.messageId)
            }

            command("report") { bot, update ->
                val message = update.message ?: return@command
                val chatId = message.chat.id
                bot.sendMessage(chatId, "command", replyToMessageId = message.messageId)
            }

            telegramError { bot, telegramError ->
                logger.warning("Telegram Error: ${telegramError.getErrorMessage()}")
            }
        }
    }

    fun run() = bot.startPolling()
}