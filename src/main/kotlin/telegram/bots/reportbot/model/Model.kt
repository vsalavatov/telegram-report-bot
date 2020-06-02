package telegram.bots.reportbot.model

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.EnumerationColumnType
import org.jetbrains.exposed.sql.`java-time`.datetime
import java.sql.Blob
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
    val minutesToGainVotePower: Column<Long> = long("minutes_to_gain_vote_power").default(60 * 24 * 7)
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
    val firstMessageDatetime: Column<LocalDateTime> = datetime("first_message_datetime").default(LocalDateTime.now().plusYears(5))
    val messages: Column<Int> = integer("total_messages").default(0)
    val banned: Column<Boolean> = bool("banned").default(false)
}

class GroupUserInfo(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<GroupUserInfo>(GroupUserInfos)

    var group by GroupInfo referencedOn GroupUserInfos.group
    var user by UserInfo referencedOn GroupUserInfos.user
    var firstMessageDatetime by GroupUserInfos.firstMessageDatetime
    var messages by GroupUserInfos.messages
    var banned by GroupUserInfos.banned

    fun hasVotePower() =
        !banned
                && messages >= group.messagesToGainVotePower
                && (group.messagesToGainVotePower == 0 || LocalDateTime.now() >= firstMessageDatetime.plusMinutes(group.minutesToGainVotePower))
}

enum class ReportVoteStatus {
    InProgress,
    Accepted,
    Rejected
}

data class VoteDataEntry(val userId: Long, val impact: Int)
typealias VoteData = List<VoteDataEntry>

object ReportVotesInfos : IntIdTable() {
    val reportedGroupUser = reference("group_user", GroupUserInfos)
    val reportedMessageId: Column<Long> = long("reported_message_id")
    val initiatorMessageId: Column<Long> = long("initiator_message_id")
    val reportInitiationDatetime: Column<LocalDateTime> = datetime("report_datetime")
    val status = enumeration("vote_status", ReportVoteStatus::class)
    val voteData = text("vote_data")
        .default(jacksonObjectMapper().writeValueAsString(listOf<VoteDataEntry>()))
    val voteMessageId = long("vote_message_id").default(-1)
}

class ReportVotesInfo(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ReportVotesInfo>(ReportVotesInfos)

    var reportedGroupUser by GroupUserInfo referencedOn ReportVotesInfos.reportedGroupUser
    var reportedMessageId by ReportVotesInfos.reportedMessageId
    var initiatorMessageId by ReportVotesInfos.initiatorMessageId
    var reportInitiationDatetime by ReportVotesInfos.reportInitiationDatetime
    var status by ReportVotesInfos.status
    var voteData by ReportVotesInfos.voteData
    var voteMessageId by ReportVotesInfos.voteMessageId

    var votes: VoteData
        get() = jacksonObjectMapper().readValue(voteData)
        set(value) {
            voteData = jacksonObjectMapper().writeValueAsString(value)
        }
    val votesCount: Int
        get() = votes.sumBy { it.impact }

    fun toggleVote(e: VoteDataEntry) {
        if (votes.any { it.userId == e.userId }) {
            votes = votes.filterNot { it.userId == e.userId }
        } else {
            votes = votes + e
        }
    }
}