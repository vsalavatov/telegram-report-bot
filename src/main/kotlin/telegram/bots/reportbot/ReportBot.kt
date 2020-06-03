package telegram.bots.reportbot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.*
import com.github.kotlintelegrambot.entities.InlineKeyboardButton
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.extensions.filters.Filter
import com.github.kotlintelegrambot.network.Response
import com.github.kotlintelegrambot.network.bimap
import com.github.kotlintelegrambot.network.fold
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.logging.HttpLoggingInterceptor
import telegram.bots.reportbot.model.*
import java.lang.IllegalArgumentException
import java.sql.Date
import java.time.LocalDateTime
import java.util.logging.Logger
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

@ExperimentalTime
class ReportBot(
    val token: String,
    val dbController: DBController,
    private val logger: Logger = Logger.getLogger("ReportBot")
) {
    private val bot: Bot = bot {
        token = this@ReportBot.token
        logLevel = HttpLoggingInterceptor.Level.BODY

        dispatch {
            message(Filter.Group and !Filter.Command) { _, update ->
                dbController.makeTransaction {
                    val message = update.message ?: return@makeTransaction
                    val author = message.from ?: return@makeTransaction
                    val chatId = message.chat.id

                    val userInfo = dbController.upsertUser(author.id) {
                        totalMessages++
                        this
                    }
                    val groupInfo = dbController.upsertGroup(chatId) { this }
                    dbController.upsertGroupUser(userInfo, groupInfo) {
                        if (messages == 0)
                            firstMessageDatetime = LocalDateTime.now()
                        messages++
                    }
                }
            }

            fun makeIntSetterHandler(
                command: String,
                block: GroupInfo.(Int) -> Unit
            ): (Bot, Update, List<String>) -> Unit = command@{ bot, update, args ->
                dbController.makeTransaction {
                    val message = update.message ?: return@makeTransaction
                    if (!Filter.Group.checkFor(message)) return@makeTransaction
                    val author = message.from ?: return@makeTransaction
                    val chatId = message.chat.id
                    val userInfo = dbController.upsertUser(author.id) { this }
                    val groupInfo = dbController.upsertGroup(chatId) { this }
                    val groupUserInfo = dbController.upsertGroupUser(userInfo, groupInfo) { this }
                    if (!groupUserInfo.isAdmin()) {
                        bot.sendMessage(chatId, "You are not an administrator.", replyToMessageId = message.messageId)
                            .deleteMessageOnSuccess(message.messageId, delayDuration = 3.seconds)
                    } else {
                        val value: Int
                        try {
                            value = args[0].toInt()
                            if (value < 0) throw IllegalArgumentException()
                        } catch (e: java.lang.Exception) {
                            bot.sendMessage(
                                chatId,
                                "Usage: /$command <int>",
                                replyToMessageId = message.messageId
                            ).deleteMessageOnSuccess(message.messageId, delayDuration = 3.seconds)
                            return@makeTransaction
                        }
                        dbController.makeTransaction {
                            groupInfo.block(value)
                        }
                        bot.sendMessage(chatId, "Done.")
                    }
                }
            }

            command("setReportVoteLimit", makeIntSetterHandler("setReportVoteLimit") { value ->
                reportVoteLimit = value
            })
            command("setMinutesToGainVotePower", makeIntSetterHandler("setMinutesToGainVotePower") { value ->
                minutesToGainVotePower = value.toLong()
            })
            command("setMessagesToGainVotePower", makeIntSetterHandler("setMessagesToGainVotePower") { value ->
                messagesToGainVotePower = value
            })

            command("report") { bot, update ->
                dbController.makeTransaction {
                    val initiatorMessage = update.message ?: return@makeTransaction
                    if (!Filter.Group.checkFor(initiatorMessage)) return@makeTransaction
                    val initiator = initiatorMessage.from ?: return@makeTransaction
                    val chatId = initiatorMessage.chat.id

                    val reportedMessage = initiatorMessage.replyToMessage ?: run {
                        bot.sendMessage(
                            chatId,
                            "Please, reply this command to the message you want to report",
                            replyToMessageId = initiatorMessage.messageId
                        ).deleteMessageOnSuccess(initiatorMessage.messageId, delayDuration = 3.seconds)
                        return@makeTransaction
                    }

                    val initiatorUserInfo = dbController.upsertUser(initiator.id) { this }
                    val groupInfo = dbController.upsertGroup(chatId) { this }
                    val initiatorGroupUserInfo = dbController.upsertGroupUser(initiatorUserInfo, groupInfo) { this }

                    val reportedUser = reportedMessage.from ?: run {
                        logger.warning("Reported message does not have an author! $reportedMessage")
                        return@makeTransaction
                    }
                    val reportedUserInfo = dbController.upsertUser(reportedUser.id) { this }
                    val reportedGroupUserInfo = dbController.upsertGroupUser(reportedUserInfo, groupInfo) { this }

                    if (reportedGroupUserInfo.isAdmin()) {
                        bot.sendMessage(
                            chatId,
                            "I cannot ban the admin lol",
                            replyToMessageId = initiatorMessage.messageId
                        ).deleteMessageOnSuccess(initiatorMessage.messageId, delayDuration = 3.seconds)

                        return@makeTransaction
                    }

                    // start voting
                    dbController.upsertReportVoteInfo(reportedGroupUserInfo, reportedMessage.messageId) { created ->
                        val isAdmin = initiatorGroupUserInfo.isAdmin()
                        if (created) {
                            this.initiatorMessageId = initiatorMessage.messageId
                            if (isAdmin) {
                                logger.info("$initiator is admin of $chatId. Banning $reportedMessage")
                                punish(this)
                                return@upsertReportVoteInfo
                            }
                            val buttons = InlineKeyboardMarkup(
                                listOf(
                                    listOf(
                                        InlineKeyboardButton(
                                            "vote progress: $votesCount/${groupInfo.reportVoteLimit}",
                                            callbackData = "vote"
                                        )
                                    )
                                )
                            )
                            bot.sendMessage(
                                chatId,
                                "vote for ban",
                                replyToMessageId = reportedMessageId,
                                replyMarkup = buttons
                            ).fold({ response ->
                                val voteMessage = response?.result ?: run {
                                    logger.warning("Didn't received any message for vote message")
                                    throw Exception("Didn't received any message for vote message")
                                }
                                voteMessageId = voteMessage.messageId
                            }, {
                                logger.warning("Couldn't create a vote button! $it")
                                throw Exception("Couldn't create a vote button! $it")
                            })
                        }
                        if (isAdmin) {
                            status = ReportVoteStatus.Accepted
                            updateVoteStatus(this)
                            punish(this)
                            return@upsertReportVoteInfo
                        }
                        if (initiatorGroupUserInfo.hasVotePower()) {
                            toggleVote(VoteDataEntry(initiator.id, 1))
                            if (updateVoteStatus(this)) {
                                punish(this)
                            }
                        }
                    }
                }
            }

            callbackQuery("vote") { bot, update ->
                dbController.makeTransaction {
                    val query = update.callbackQuery ?: run {
                        logger.warning("Expected a callbackQuery but none was found")
                        return@makeTransaction
                    }
                    val message = query.message ?: run {
                        logger.warning("Expected a message inside a query but none was found")
                        return@makeTransaction
                    }
                    val voteMessageId = query.message?.messageId ?: run {
                        logger.warning("Could not retrieve message id from callback query")
                        return@makeTransaction
                    }
                    dbController.updateReportVoteInfo(voteMessageId) {
                        if (this == null) {
                            logger.warning("Unexistent vote message id: $voteMessageId in callback: ${update.callbackQuery}")
                        } else {
                            val userId = query.from.id
                            val groupId = message.chat.id
                            val userInfo = dbController.upsertUser(userId) { this }
                            val groupInfo = dbController.upsertGroup(groupId) { this }
                            val groupUserInfo = dbController.upsertGroupUser(userInfo, groupInfo) { this }
                            if (groupUserInfo.isAdmin()) {
                                status = ReportVoteStatus.Accepted
                                updateVoteStatus(this)
                                punish(this)
                                bot.answerCallbackQuery(
                                    query.id,
                                    "You've used your omnipotence to punish this unruly member",
                                    showAlert = true
                                )
                            } else if (groupUserInfo.hasVotePower()) {
                                toggleVote(VoteDataEntry(userId, 1))
                                if (updateVoteStatus(this)) {
                                    punish(this)
                                }
                                Unit
                            } else {
                                bot.answerCallbackQuery(
                                    query.id,
                                    "You don't have the vote power yet.",
                                    showAlert = true
                                )
                            }
                        }
                    }
                }
            }
            telegramError { _, telegramError ->
                logger.warning("Telegram Error: ${telegramError.getErrorMessage()}")
            }
        }
    }

    private fun punish(votesInfo: ReportVotesInfo) {
        votesInfo.status = ReportVoteStatus.Accepted
        val chatId = votesInfo.reportedGroupUser.group.groupId
        bot.deleteMessage(chatId, votesInfo.reportedMessageId)
        bot.deleteMessage(chatId, votesInfo.initiatorMessageId)
        GlobalScope.launch {
            delay(3.seconds)
            bot.deleteMessage(chatId, votesInfo.voteMessageId)
        }

        val reportedUser = votesInfo.reportedGroupUser.user
        val untilDate = Date.valueOf(LocalDateTime.now().plusYears(2).toLocalDate())
        bot.kickChatMember(chatId, reportedUser.userId, untilDate).fold({ response ->
            if (response?.ok == true) {
                dbController.makeTransaction {
                    reportedUser.confirmedReports++
                }
            } else {
                logger.warning("Could not ban ${votesInfo.reportedGroupUser}!")
            }
        }, { error ->
            logger.warning("Could not ban ${votesInfo.reportedGroupUser}: $error")
        })

    }

    private fun updateVoteStatus(reportVotesInfo: ReportVotesInfo): Boolean {
        val buttons: InlineKeyboardMarkup
        val acceptReport: Boolean
        if (reportVotesInfo.votesCount >= reportVotesInfo.reportedGroupUser.group.reportVoteLimit
            || reportVotesInfo.status == ReportVoteStatus.Accepted
        ) {
            buttons = InlineKeyboardMarkup(
                listOf(
                    listOf(
                        InlineKeyboardButton(
                            "report accepted",
                            callbackData = "done"
                        )
                    )
                )
            )
            acceptReport = true
        } else {
            buttons = InlineKeyboardMarkup(
                listOf(
                    listOf(
                        InlineKeyboardButton(
                            "vote progress: ${reportVotesInfo.votesCount}/${reportVotesInfo.reportedGroupUser.group.reportVoteLimit}",
                            callbackData = "vote"
                        )
                    )
                )
            )
            acceptReport = false
        }
        bot.editMessageReplyMarkup(
            reportVotesInfo.reportedGroupUser.group.groupId,
            reportVotesInfo.voteMessageId,
            replyMarkup = buttons
        )
        return acceptReport
    }

    private fun GroupUserInfo.isAdmin(): Boolean =
        bot.getChatAdministrators(group.groupId).bimap({ response ->
            val admins = response?.result ?: return@bimap false // TODO: cache admins?
            logger.info("${group.groupId} admins: $admins")
            admins.any {
                it.user.id == user.userId
            }
        }, {
            logger.warning("Couldn't retrieve chat admins: $it")
            false
        })

    private fun Pair<retrofit2.Response<Response<Message>?>?, Exception?>.deleteMessageOnSuccess(vararg otherMessageId: Long, delayDuration: Duration = 0.seconds) {
        this.fold({
            val chatId = it?.result?.chat?.id ?: return@fold
            it.result?.messageId?.let { replyId ->
                GlobalScope.launch {
                    delay(delayDuration)
                    bot.deleteMessage(chatId, replyId)
                    otherMessageId.forEach {
                        bot.deleteMessage(chatId, it)
                    }
                }
            }
        })
    }

    fun run() = bot.startPolling()
}