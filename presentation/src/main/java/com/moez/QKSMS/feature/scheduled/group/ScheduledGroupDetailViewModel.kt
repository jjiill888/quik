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
package dev.octoshrimpy.quik.feature.scheduled.group

import android.content.Context
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.Navigator
import dev.octoshrimpy.quik.common.base.QkViewModel
import dev.octoshrimpy.quik.common.util.ClipboardUtils
import dev.octoshrimpy.quik.interactor.DeleteScheduledMessages
import dev.octoshrimpy.quik.interactor.SendScheduledMessage
import dev.octoshrimpy.quik.repository.ScheduledGroupRepository
import dev.octoshrimpy.quik.repository.ScheduledMessageRepository
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject
import javax.inject.Named

class ScheduledGroupDetailViewModel @Inject constructor(
    @Named("groupId") private val groupId: Long,
    private val context: Context,
    private val navigator: Navigator,
    private val scheduledGroupRepo: ScheduledGroupRepository,
    private val scheduledMessageRepo: ScheduledMessageRepository,
    private val sendScheduledMessageInteractor: SendScheduledMessage,
    private val deleteScheduledMessagesInteractor: DeleteScheduledMessages
) : QkViewModel<ScheduledGroupDetailView, ScheduledGroupDetailState>(
    ScheduledGroupDetailState(groupId = groupId)
) {

    init {
        loadGroupData()
    }

    override fun bindView(view: ScheduledGroupDetailView) {
        super.bindView(view)

        // Update the state when the message selected count changes
        view.messagesSelectedIntent
            .map { selection -> selection.size }
            .autoDisposable(view.scope())
            .subscribe { newState { copy(selectedMessages = it) } }

        // Toggle select all / select none
        view.optionsItemIntent
            .filter { it == R.id.select_all }
            .autoDisposable(view.scope())
            .subscribe { view.toggleSelectAll() }

        // Show the delete message dialog if one or more messages selected
        view.optionsItemIntent
            .filter { it == R.id.delete }
            .withLatestFrom(view.messagesSelectedIntent) { _, selectedMessages ->
                selectedMessages
            }
            .autoDisposable(view.scope())
            .subscribe {
                val ids = it.mapNotNull(scheduledMessageRepo::getScheduledMessage)
                    .map { it.id }
                view.showDeleteDialog(ids)
            }

        // Copy the selected message text to the clipboard
        view.optionsItemIntent
            .filter { it == R.id.copy }
            .withLatestFrom(view.messagesSelectedIntent) { _, selectedMessages ->
                selectedMessages
            }
            .autoDisposable(view.scope())
            .subscribe {
                val messages = it
                    .mapNotNull(scheduledMessageRepo::getScheduledMessage)
                    .sortedBy { it.date }
                val text = when (messages.size) {
                    1 -> messages.first().body
                    else -> messages.fold(StringBuilder()) { acc, message ->
                        if (acc.isNotEmpty() && message.body.isNotEmpty())
                            acc.append("\n\n")
                        acc.append(message.body)
                    }
                }

                ClipboardUtils.copy(context, text.toString())
            }

        // Send the messages now menu item selected
        view.optionsItemIntent
            .filter { it == R.id.send_now }
            .withLatestFrom(view.messagesSelectedIntent) { _, selectedMessages ->
                selectedMessages
            }
            .autoDisposable(view.scope())
            .subscribe { view.showSendNowDialog(it) }

        // Edit message menu item selected
        view.optionsItemIntent
            .filter { it == R.id.edit_message }
            .withLatestFrom(view.messagesSelectedIntent) { _, selectedMessage ->
                selectedMessage.first()
            }
            .autoDisposable(view.scope())
            .subscribe { view.showEditMessageDialog(it) }

        // Delete message(s) (fired after the confirmation dialog has been shown)
        view.deleteScheduledMessages
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .autoDisposable(view.scope())
            .subscribe {
                deleteScheduledMessagesInteractor.execute(it)
                view.clearSelection()
            }

        // Send message(s) now (fired after the confirmation dialog has been shown)
        view.sendScheduledMessages
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .autoDisposable(view.scope())
            .subscribe {
                it.forEach { sendScheduledMessageInteractor.execute(it) }
                view.clearSelection()
            }

        // Edit message (fired after the confirmation dialog has been shown)
        view.editScheduledMessage
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .autoDisposable(view.scope())
            .subscribe {
                scheduledMessageRepo.getScheduledMessage(it)?.let {
                    navigator.showCompose(it)
                    scheduledMessageRepo.deleteScheduledMessage(it.id)
                }
                view.clearSelection()
            }

        // Navigate back or unselect
        view.optionsItemIntent
            .filter { it == android.R.id.home }
            .map { }
            .mergeWith(view.backPressedIntent)
            .withLatestFrom(state) { _, state -> state }
            .autoDisposable(view.scope())
            .subscribe {
                when {
                    (it.selectedMessages > 0) -> view.clearSelection()
                    else -> view.finishActivity()
                }
            }

        // Add message button clicked
        view.addMessageIntent
            .autoDisposable(view.scope())
            .subscribe {
                // TODO: Navigate to compose with groupId parameter
                navigator.showCompose(mode = "scheduling")
                view.clearSelection()
            }
    }

    private fun loadGroupData() {
        val group = scheduledGroupRepo.getScheduledGroup(groupId)
        val messages = scheduledGroupRepo.getScheduledMessagesForGroup(groupId)

        newState {
            copy(
                groupName = group?.name ?: "Unknown Group",
                groupDescription = group?.description ?: "",
                scheduledMessages = messages,
                hasMessages = !messages.isEmpty(),
                loading = false
            )
        }
    }
}
