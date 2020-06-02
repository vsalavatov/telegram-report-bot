package telegram.bots.reportbot

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import telegram.bots.reportbot.model.*
import java.net.Authenticator
import java.net.PasswordAuthentication
import java.util.logging.Logger
import kotlin.time.ExperimentalTime

val logger = Logger.getLogger("ReportBot")

data class Config(
    val token: String,
    val dbPath: String,
    val logger: Logger
)
class IncorrectConfigException(desc: String) : Exception(desc)

fun parseConfig(args: Array<String>): Config {
    var token: String? = null
    var dbPath = "./bot.db"
    var i = 0
    while (i < args.size) {
        if (args[i] == "--token") {
            if (i + 1 >= args.size)
                throw IncorrectConfigException("Bot token is not provided")
            i++

            token = args[i]
        } else if (args[i] == "--test") {
            dbPath = "mem:test"
        } else if (args[i] == "--db") {
            if (i + 1 >= args.size)
                throw IncorrectConfigException("DB path is not provided")
            i++

            dbPath = args[i]
        } else if (args[i] == "--socks") {
            if (i + 1 >= args.size)
                throw IncorrectConfigException("Socks5 proxy URL is not provided")
            i++

            val (hostname, port) = Regex("(.*):(\\d+)").matchEntire(args[i])?.destructured
                ?: throw IncorrectConfigException("Socks5 URL must be in the form of <hostname>:<port>")

            System.setProperty("socksProxyHost", hostname)
            System.setProperty("socksProxyPort", port)
        } else if (args[i] == "--socks-auth") {
            if (i + 1 >= args.size)
                throw IncorrectConfigException("Sock5 auth data is not provided (format: <user:pass>)")
            i++

            val (username, password) = Regex("(.*):(.*)").matchEntire(args[i])?.destructured
                ?: throw IncorrectConfigException("Socks5 auth data must be in the form of <hostname>:<port>")

            System.setProperty("java.net.socks.username", username)
            System.setProperty("java.net.socks.password", password)
            Authenticator.setDefault(object: Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(username, password.toCharArray())
                }
            })
        } else {
            logger.warning("Unused argument \"${args[i]}\"")
        }
        i++
    }
    return Config(
        token ?: throw IncorrectConfigException("You must specify the bot token"),
        dbPath,
        logger
    )
}

@ExperimentalTime
fun main(args: Array<String>) {
    val config = parseConfig(args)
    val db = Database.connect("jdbc:h2:" + config.dbPath)

    transaction(db) {
        SchemaUtils.createMissingTablesAndColumns(UserInfos, GroupInfos, GroupUserInfos, ReportVotesInfos)
    }

    val bot = ReportBot(config.token, DBController(db), logger)
    bot.run()
}