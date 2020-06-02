package telegram.bots.reportbot.model

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.`java-time`.datetime
import java.time.LocalDateTime
import java.util.*

object UserInfos : IntIdTable() {
    val userId: Column<Long> = long("user_id")
    val totalMessages: Column<Int> = integer("total_messages").default(0)
    val confirmedReports: Column<Int> = integer("confirmed_reports").default(0)
}

class UserInfo(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<UserInfo>(UserInfos)

    var userId by UserInfos.userId
    var totalMessages by UserInfos.totalMessages
    var confirmedReports by UserInfos.confirmedReports
}

object GroupInfos : IntIdTable() {
    val groupId: Column<Long> = long("group_id")
    val reportVoteLimit: Column<Int> = integer("report_vote_limit").default(10)
    val minutesToGainVotePower: Column<Long> = long("minutes_to_gain_vote_power").default(60*24*7)
    val messagesToGainVotePower: Column<Int> = integer("messages_to_gain_vote_power").default(20)
}

class GroupInfo(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<GroupInfo>(GroupInfos)

    var groupId by GroupInfos.groupId
    var reportVoteLimit by GroupInfos.reportVoteLimit
    var minutesToGainVotePower by GroupInfos.minutesToGainVotePower
    var messagesToGainVotePower by GroupInfos.messagesToGainVotePower
}

object GroupUserInfos : IntIdTable() {
    val group = reference("group", GroupInfos)
    val user = reference("user", UserInfos)
    val firstMessageDatetime: Column<LocalDateTime> = datetime("first_message_datetime").default(LocalDateTime.MAX)
    val messages: Column<Int> = integer("total_messages").default(0)
    val banned: Column<Boolean> = bool("banned").default(false)
}

class GroupUserInfo(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<GroupUserInfo>(GroupUserInfos)

    var group by GroupInfo referencedOn GroupUserInfos.group
    var user by UserInfo referencedOn GroupUserInfos.user
    var firstMessageDatetime by GroupUserInfos.firstMessageDatetime
    var messages by GroupUserInfos.messages
    var banned by GroupUserInfos.banned

    fun hasVotePower() =
        LocalDateTime.now() >= firstMessageDatetime.plusMinutes(group.minutesToGainVotePower)
                && messages >= group.messagesToGainVotePower
                && !banned
}