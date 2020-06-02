package telegram.bots.reportbot.model

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class DBController(val db: Database) {
    private val lock = ReentrantLock()

    fun <R> makeTransaction(block: Transaction.() -> R): R = lock.withLock {
        transaction(db) {
            block()
        }
    }

    fun <R> upsertUser(userId: Long, block: UserInfo.() -> R): R = makeTransaction {
        val user: UserInfo
        UserInfo.find { UserInfos.userId eq userId }.toList().let {
            if (it.isEmpty()) {
                user = UserInfo.new {
                    this.userId = userId
                }
            } else {
                user = it[0]
            }
        }
        user.block()
    }

    fun <R> upsertGroup(groupId: Long, block: GroupInfo.() -> R): R = makeTransaction {
        val group: GroupInfo
        GroupInfo.find { GroupInfos.groupId eq groupId }.toList().let {
            if (it.isEmpty()) {
                group = GroupInfo.new {
                    this.groupId = groupId
                }
            } else {
                group = it[0]
            }
        }
        group.block()
    }


    fun <R> upsertGroupUser(user: UserInfo, group: GroupInfo, block: GroupUserInfo.() -> R): R = makeTransaction {
        val groupUser: GroupUserInfo
        GroupUserInfo.find {
            (GroupUserInfos.group eq group.id) and (GroupUserInfos.user eq user.id)
        }.toList().let {
            if (it.isEmpty()) {
                groupUser = GroupUserInfo.new {
                    this.group = group
                    this.user = user
                }
            } else {
                groupUser = it[0]
            }
        }
        groupUser.block()
    }


    fun <R> upsertReportVoteInfo(groupUser: GroupUserInfo, messageId: Long, block: ReportVotesInfo.(Boolean) -> R): R =
        makeTransaction {
            val reportVotesInfo: ReportVotesInfo
            val created: Boolean
            ReportVotesInfo.find {
                (ReportVotesInfos.reportedGroupUser eq groupUser.id) and (ReportVotesInfos.reportedMessageId eq messageId)
            }.toList().let {
                if (it.isEmpty()) {
                    reportVotesInfo = ReportVotesInfo.new {
                        reportedGroupUser = groupUser
                        reportedMessageId = messageId
                        reportInitiationDatetime = LocalDateTime.now()
                        status = ReportVoteStatus.InProgress
                    }
                    created = true
                } else {
                    reportVotesInfo = it[0]
                    created = false
                }
            }
            reportVotesInfo.block(created)
        }

    fun <R> updateReportVoteInfo(voteMessageId: Long, block: ReportVotesInfo?.() -> R): R =
        makeTransaction {
            ReportVotesInfo.find {
                ReportVotesInfos.voteMessageId eq voteMessageId
            }.toList().let {
                if (it.isEmpty())
                    null.block()
                else
                    it[0].block()
            }
        }
}