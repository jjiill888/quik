/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.octoshrimpy.quik.interactor

import android.content.Context
import android.net.Uri
import dev.octoshrimpy.quik.compat.TelephonyCompat
import dev.octoshrimpy.quik.extensions.mapNotNull
import dev.octoshrimpy.quik.model.Attachment
import dev.octoshrimpy.quik.repository.ScheduledMessageRepository
import io.reactivex.Flowable
import io.reactivex.rxkotlin.toFlowable
import io.realm.RealmList
import javax.inject.Inject

class SendScheduledMessage @Inject constructor(
    private val context: Context,
    private val scheduledMessageRepo: ScheduledMessageRepository,
    private val deleteScheduledMessagesInteractor: DeleteScheduledMessages,
    private val sendMessage: SendMessage
) : Interactor<Long>() {

    override fun buildObservable(params: Long): Flowable<*> {
        return Flowable.just(params)
            .mapNotNull(scheduledMessageRepo::getScheduledMessage)
            .flatMap { message ->
                // Split into individual messages if not sending as group
                val messages = if (message.sendAsGroup) {
                    listOf(message)
                } else {
                    message.recipients.map { recipient -> message.copy(recipients = RealmList(recipient)) }
                }

                // Send all the messages
                messages.map { msg ->
                    val threadId = TelephonyCompat.getOrCreateThreadId(context, msg.recipients)
                    val attachments = msg.attachments.mapNotNull(Uri::parse).map { Attachment(context, it) }
                    SendMessage.Params(msg.subId, threadId, msg.recipients, msg.body, attachments)
                }.toFlowable()
                    .flatMap(sendMessage::buildObservable)
                    // Return the original message after all sends complete
                    .toList()
                    .map { message }
                    .toFlowable()
            }
            .doOnNext {
                // Mark message as completed or delete it (only executes once per scheduled message)
                val message = scheduledMessageRepo.getScheduledMessage(params)
                if (message != null && message.groupId != 0L) {
                    // If message is part of a group, mark it as completed
                    scheduledMessageRepo.markScheduledMessageComplete(params)
                } else {
                    // If message is not part of a group, delete it (old behavior)
                    deleteScheduledMessagesInteractor.execute(listOf(params))
                }
            }
    }

}
