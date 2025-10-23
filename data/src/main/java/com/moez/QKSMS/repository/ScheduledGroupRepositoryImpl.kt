package dev.octoshrimpy.quik.repository

import dev.octoshrimpy.quik.model.ScheduledGroup
import dev.octoshrimpy.quik.model.ScheduledMessage
import io.realm.Realm
import io.realm.RealmResults
import io.realm.Sort
import javax.inject.Inject

class ScheduledGroupRepositoryImpl @Inject constructor() : ScheduledGroupRepository {

    override fun createScheduledGroup(name: String, description: String): ScheduledGroup {
        Realm.getDefaultInstance().use { realm ->
            val id = (realm
                .where(ScheduledGroup::class.java)
                .max("id")
                ?.toLong() ?: -1
                    ) + 1

            val now = System.currentTimeMillis()
            val group = ScheduledGroup(
                id = id,
                name = name,
                description = description,
                createdAt = now,
                updatedAt = now
            )

            realm.executeTransaction { realm.insertOrUpdate(group) }

            return group
        }
    }

    override fun getScheduledGroups(): RealmResults<ScheduledGroup> {
        return Realm.getDefaultInstance()
            .where(ScheduledGroup::class.java)
            .sort("createdAt")
            .findAll()
    }

    override fun getScheduledGroup(id: Long): ScheduledGroup? {
        return Realm.getDefaultInstance()
            .where(ScheduledGroup::class.java)
            .equalTo("id", id)
            .findFirst()
    }

    override fun getScheduledMessagesForGroup(groupId: Long): RealmResults<ScheduledMessage> {
        return Realm.getDefaultInstance()
            .where(ScheduledMessage::class.java)
            .equalTo("groupId", groupId)
            .sort(
                arrayOf("completed", "completedAt", "id"),
                arrayOf(
                    Sort.ASCENDING,  // incomplete messages (false) first
                    Sort.DESCENDING, // most recently completed messages first
                    Sort.DESCENDING  // newest creations first for incomplete items
                )
            )
            .findAll()
    }

    override fun updateScheduledGroup(group: ScheduledGroup) {
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction {
                group.updatedAt = System.currentTimeMillis()
                realm.insertOrUpdate(group)
            }
        }
    }

    override fun deleteScheduledGroup(id: Long) {
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction {
                realm.where(ScheduledGroup::class.java)
                    .equalTo("id", id)
                    .findFirst()
                    ?.deleteFromRealm()
            }
        }
    }

}
