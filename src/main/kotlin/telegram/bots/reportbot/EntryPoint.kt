package telegram.bots.reportbot

import org.jetbrains.exposed.sql.Database
import java.util.logging.Logger

val logger = Logger.getLogger("ReportBot")

data class Config(val token: String, val dbPath: String)
class IncorrectConfigException(desc: String) : Exception(desc)

fun parseConfig(args: Array<String>): Config {
    var token: String? = null
    var dbPath = "bot.db"
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
        }
        else {
            logger.warning("Unused argument \"${args[i]}\"")
        }
        i++
    }
    return Config(
        token ?: throw IncorrectConfigException("You must specify the bot token"),
        dbPath
    )
}

fun main(args: Array<String>) {
    val config = parseConfig(args)
    val db = Database.connect("jdbc:h2:" + config.dbPath)
    val bot = ReportBot(config.token, db, logger)
    bot.run()
}