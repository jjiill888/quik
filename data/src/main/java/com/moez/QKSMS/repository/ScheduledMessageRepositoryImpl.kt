package dev.octoshrimpy.quik.repository

import dev.octoshrimpy.quik.model.ScheduledMessage
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmResults
import timber.log.Timber
import javax.inject.Inject

class ScheduledMessageRepositoryImpl @Inject constructor() : ScheduledMessageRepository {

    private val disposables = CompositeDisposable()

    override fun saveScheduledMessage(
        date: Long,
        subId: Int,
        recipients: List<String>,
        sendAsGroup: Boolean,
        body: String,
        attachments: List<String>,
        conversationId: Long,
        groupId: Long
    ): ScheduledMessage {
        Timber.d("ScheduledMessageRepo: saveScheduledMessage called with groupId=$groupId")
        Realm.getDefaultInstance().use { realm ->
            val id = (realm
                .where(ScheduledMessage::class.java)
                .max("id")
                ?.toLong() ?: -1
                    ) + 1

            val recipientsRealmList = RealmList(*recipients.toTypedArray())
            val attachmentsRealmList = RealmList(*attachments.toTypedArray())

            val message = ScheduledMessage(id, date, subId, recipientsRealmList, sendAsGroup, body,
                attachmentsRealmList, conversationId, groupId)

            Timber.d("ScheduledMessageRepo: Created message id=$id, groupId=$groupId")
            realm.executeTransaction { realm.insertOrUpdate(message) }

            Timber.d("ScheduledMessageRepo: Message saved id=$id, groupId=${message.groupId}")
            return message
        }
    }

    override fun updateScheduledMessage(scheduledMessage: ScheduledMessage) {
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction { realm.insertOrUpdate(scheduledMessage) }
        }
    }

    override fun getScheduledMessages(): RealmResults<ScheduledMessage> {
        return Realm.getDefaultInstance()
            .where(ScheduledMessage::class.java)
            .sort(
                arrayOf("completed", "date", "completedAt"),
                arrayOf(
                    io.realm.Sort.ASCENDING,  // incomplete messages (false) first
                    io.realm.Sort.ASCENDING,  // upcoming scheduled messages first for incomplete items
                    io.realm.Sort.DESCENDING  // most recently completed messages first
                )
            )
            .findAll()
    }

    override fun getScheduledMessage(id: Long): ScheduledMessage? {
        return Realm.getDefaultInstance()
            .apply { refresh() }
            .where(ScheduledMessage::class.java)
            .equalTo("id", id)
            .findFirst()
    }

    override fun getScheduledMessagesForConversation(conversationId: Long): RealmResults<ScheduledMessage> {
        return Realm.getDefaultInstance()
            .where(ScheduledMessage::class.java)
            .equalTo("conversationId", conversationId)
            .sort(
                arrayOf("completed", "date", "completedAt"),
                arrayOf(
                    io.realm.Sort.ASCENDING,  // incomplete messages (false) first
                    io.realm.Sort.ASCENDING,  // upcoming scheduled messages first for incomplete items
                    io.realm.Sort.DESCENDING  // most recently completed messages first
                )
            )
            .findAllAsync()
    }

    override fun deleteScheduledMessage(id: Long) {
        val subscription = Completable.fromAction {
            Realm.getDefaultInstance().use { realm ->
                val message = realm.where(ScheduledMessage::class.java)
                    .equalTo("id", id)
                    .findFirst()

                realm.executeTransaction { message?.deleteFromRealm() }
            }
        }.subscribeOn(Schedulers.io()) // Run on a background thread and switch to main if needed
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                Timber.v("Successfully deleted scheduled messages.")
            }, {
                Timber.e("Deleting scheduled messages failed.")
            })

        disposables.add(subscription)
    }

    override fun deleteScheduledMessages(ids: List<Long>) {
        ids.forEach { deleteScheduledMessage(it) }
    }

    override fun getAllScheduledMessageIdsSnapshot(): List<Long> {
        Realm.getDefaultInstance().use { realm ->
            return realm
                .where(ScheduledMessage::class.java)
                .sort("date")
                .findAll()
                .createSnapshot()
                .map { it.id }
        }
    }

    override fun markScheduledMessageComplete(id: Long) {
        Timber.d("ScheduledMessageRepo: markScheduledMessageComplete called for id=$id")
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction {
                val message = realm.where(ScheduledMessage::class.java)
                    .equalTo("id", id)
                    .findFirst()

                if (message != null) {
                    Timber.d("ScheduledMessageRepo: Found message id=$id, groupId=${message.groupId}, setting completed=true")
                    message.completed = true
                    message.completedAt = System.currentTimeMillis()
                    Timber.d("ScheduledMessageRepo: Message marked - completed=${message.completed}, completedAt=${message.completedAt}")
                } else {
                    Timber.e("ScheduledMessageRepo: Message id=$id NOT FOUND!")
                }
            }
        }
        Timber.d("ScheduledMessageRepo: markScheduledMessageComplete finished for id=$id")
    }
}
