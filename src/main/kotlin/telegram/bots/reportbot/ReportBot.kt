package telegram.bots.reportbot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.*
import com.github.kotlintelegrambot.extensions.filters.Filter
import com.github.kotlintelegrambot.network.fold
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import telegram.bots.reportbot.model.DBController
import java.time.LocalDateTime
import java.util.logging.Logger
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

@ExperimentalTime
class ReportBot(
    val token: String,
    val db: DBController,
    private val logger: Logger = Logger.getLogger("ReportBot")
) {
    private val bot: Bot = bot {
        token = this@ReportBot.token

        dispatch {
            message(Filter.Group and !Filter.Command) { bot, update ->
                val message = update.message ?: return@message
                val author = message.from ?: return@message
                val chatId = message.chat.id

                val userInfo = db.upsertUser(author.id) {
                    totalMessages++
                    this
                }
                val groupInfo = db.upsertGroup(chatId) { this }
                val groupUserInfo = db.upsertGroupUser(userInfo, groupInfo) {
                    messages++
                    if (firstMessageDatetime == LocalDateTime.MAX)
                        firstMessageDatetime = LocalDateTime.now()
                    this
                }
            }

            command("report") { bot, update ->
                val message = update.message ?: return@command
                val author = message.from ?: return@command
                val chatId = message.chat.id

                val userInfo = db.upsertUser(author.id) { this }
                val groupInfo = db.upsertGroup(chatId) { this }

                val groupUserInfo = db.upsertGroupUser(userInfo, groupInfo) { this }

                message.replyToMessage ?: run {
                    val reply = bot.sendMessage(
                        chatId,
                        "Please, reply this command to the message you want to report",
                        replyToMessageId = message.messageId
                    )
                    reply.fold({ response ->
                        response?.result?.messageId?.let { replyId ->
                            GlobalScope.launch {
                                delay(5.seconds)
                                bot.deleteMessage(chatId, replyId)
                                bot.deleteMessage(chatId, message.messageId)
                            }
                        }
                    })
                }
            }

            telegramError { bot, telegramError ->
                logger.warning("Telegram Error: ${telegramError.getErrorMessage()}")
            }
        }
    }

    fun run() = bot.startPolling()
}