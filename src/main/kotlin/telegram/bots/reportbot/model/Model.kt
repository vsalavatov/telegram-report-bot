package telegram.bots.reportbot.model

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.`java-time`.datetime
import java.time.LocalDateTime
import java.util.*

object UserInfos : LongIdTable() {
    val totalMessages: Column<Int> = integer("total_messages")
    val confirmedReports: Column<Int> = integer("confirmed_reports")
}

class UserInfo(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<UserInfo>(UserInfos)

    var userId by UserInfos.id
    var totalMessages by UserInfos.totalMessages
    var confirmedReports by UserInfos.confirmedReports
}

object GroupInfos : LongIdTable() {
    val reportVoteLimit: Column<Int> = integer("report_vote_limit")
}

class GroupInfo(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<GroupInfo>(GroupInfos)

    var groupId by GroupInfos.id
    var reportVoteLimit by GroupInfos.reportVoteLimit
}

object GroupUserInfos : UUIDTable() {
    val group = reference("group", GroupInfos)
    val user = reference("user", UserInfos)
    val firstMessageDatetime: Column<LocalDateTime> = datetime("first_message_datetime")
    val messages: Column<Int> = integer("total_messages")
    val votePower: Column<Int> = integer("vote_power")
    val banned: Column<Boolean> = bool("banned")
}

class GroupUserInfo(id: EntityID<UUID>): UUIDEntity(id) {
    companion object : UUIDEntityClass<GroupUserInfo>(GroupUserInfos)

    var group by GroupInfo referencedOn GroupUserInfos.group
    var use by UserInfo referencedOn GroupUserInfos.user
    var firstMessageDatetime by GroupUserInfos.firstMessageDatetime
    var messages by GroupUserInfos.messages
    var votePower by GroupUserInfos.votePower
    var banned by GroupUserInfos.banned
}