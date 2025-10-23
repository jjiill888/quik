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

import dev.octoshrimpy.quik.repository.ScheduledGroupRepository
import dev.octoshrimpy.quik.repository.ScheduledMessageRepository
import io.reactivex.Completable
import io.reactivex.Flowable
import javax.inject.Inject

class CreateScheduledGroupWithMessages @Inject constructor(
    private val scheduledGroupRepository: ScheduledGroupRepository,
    private val scheduledMessageRepository: ScheduledMessageRepository,
    private val updateScheduledMessageAlarms: UpdateScheduledMessageAlarms
) : Interactor<CreateScheduledGroupWithMessages.Params>() {

    data class Message(
        val scheduledAtMillis: Long,
        val phoneNumber: String,
        val body: String,
        val name: String?
    )

    data class Params(
        val name: String,
        val description: String,
        val messages: List<Message>
    )

    override fun buildObservable(params: Params): Flowable<*> {
        return Flowable.fromCallable {
            val group = scheduledGroupRepository.createScheduledGroup(params.name, params.description)

            params.messages.forEach { message ->
                scheduledMessageRepository.saveScheduledMessage(
                    date = message.scheduledAtMillis,
                    subId = -1,
                    recipients = listOf(message.phoneNumber),
                    sendAsGroup = false,
                    body = message.body,
                    attachments = emptyList(),
                    conversationId = 0,
                    groupId = group.id
                )
            }

            Completable.fromPublisher(updateScheduledMessageAlarms.buildObservable(Unit))
                .blockingAwait()

            group.id
        }
    }
}
