package telegram.bots.reportbot.model

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class DBController(val db: Database) {
    fun <R> upsertUser(userId: Long, block: UserInfo.() -> R): R = transaction(db) {
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

    fun <R> upsertGroup(groupId: Long, block: GroupInfo.() -> R): R = transaction(db) {
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

    fun <R> upsertGroupUser(user: UserInfo, group: GroupInfo, block: GroupUserInfo.() -> R): R = transaction(db) {
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
}