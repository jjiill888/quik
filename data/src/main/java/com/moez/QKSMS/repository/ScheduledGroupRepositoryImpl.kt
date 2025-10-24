package dev.octoshrimpy.quik.repository

import dev.octoshrimpy.quik.model.ScheduledGroup
import dev.octoshrimpy.quik.model.ScheduledMessage
import io.realm.Realm
import io.realm.RealmResults
import io.realm.Sort
import javax.inject.Inject

class ScheduledGroupRepositoryImpl @Inject constructor() : ScheduledGroupRepository {

    override fun createScheduledGroup(name: String, description: String): ScheduledGroup {
        var createdGroupId: Long
        Realm.getDefaultInstance().use { realm ->
            val maxId = realm
                .where(ScheduledGroup::class.java)
                .max("id")
            android.util.Log.d("ScheduledGroupRepo", "createScheduledGroup: maxId=$maxId")

            // Start from 1 instead of 0, since groupId=0 means "not in any group"
            val id = (maxId?.toLong() ?: 0) + 1
            android.util.Log.d("ScheduledGroupRepo", "createScheduledGroup: new id=$id")

            createdGroupId = id

            val now = System.currentTimeMillis()
            val group = ScheduledGroup(
                id = id,
                name = name,
                description = description,
                createdAt = now,
                updatedAt = now
            )

            realm.executeTransaction { realm.insertOrUpdate(group) }
            android.util.Log.d("ScheduledGroupRepo", "createScheduledGroup: saved group with id=$id")
        }

        // Query the group again from a fresh realm instance to get a managed object
        return Realm.getDefaultInstance().use { realm ->
            val found = realm.where(ScheduledGroup::class.java)
                .equalTo("id", createdGroupId)
                .findFirst()
            android.util.Log.d("ScheduledGroupRepo", "createScheduledGroup: found group with id=${found?.id}")

            found!!.let { realm.copyFromRealm(it) }.also {
                android.util.Log.d("ScheduledGroupRepo", "createScheduledGroup: returning group with id=${it.id}")
            }
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
            realm.executeTransaction { transactionRealm ->
                transactionRealm.where(ScheduledMessage::class.java)
                    .equalTo("groupId", id)
                    .findAll()
                    .deleteAllFromRealm()

                transactionRealm.where(ScheduledGroup::class.java)
                    .equalTo("id", id)
                    .findFirst()
                    ?.deleteFromRealm()
            }
        }
    }

}
