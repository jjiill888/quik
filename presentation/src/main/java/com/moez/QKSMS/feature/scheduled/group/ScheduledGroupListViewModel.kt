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

import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.Navigator
import dev.octoshrimpy.quik.common.base.QkViewModel
import dev.octoshrimpy.quik.repository.ScheduledGroupRepository
import dev.octoshrimpy.quik.repository.ScheduledMessageRepository
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class ScheduledGroupListViewModel @Inject constructor(
    private val navigator: Navigator,
    private val scheduledGroupRepo: ScheduledGroupRepository,
    private val scheduledMessageRepo: ScheduledMessageRepository
) : QkViewModel<ScheduledGroupListView, ScheduledGroupListState>(ScheduledGroupListState()) {

    override fun bindView(view: ScheduledGroupListView) {
        super.bindView(view)

        // Load all scheduled groups
        val groups = scheduledGroupRepo.getScheduledGroups()
        newState { copy(groups = groups) }

        // Update the state when the group selected count changes
        view.groupsSelectedIntent
            .map { selection -> selection.size }
            .autoDisposable(view.scope())
            .subscribe { newState { copy(selectedGroups = it) } }

        // Navigate to group detail when clicked
        view.groupClicks
            .autoDisposable(view.scope())
            .subscribe { groupId -> navigator.showScheduledGroupDetail(groupId) }

        // Navigate to create group
        view.createGroupIntent
            .autoDisposable(view.scope())
            .subscribe { navigator.showScheduledGroupCreate() }

        // Show the delete dialog if groups are selected
        view.optionsItemIntent
            .filter { it == R.id.delete }
            .withLatestFrom(view.groupsSelectedIntent) { _, selectedGroups ->
                selectedGroups }
            .autoDisposable(view.scope())
            .subscribe {
                val ids = it.mapNotNull(scheduledGroupRepo::getScheduledGroup)
                    .map { it.id }
                view.showDeleteDialog(ids)
            }

        // Delete groups (fired after the confirmation dialog has been shown)
        view.deleteScheduledGroups
            .doOnNext { groupIds ->
                // Perform deletion on IO thread
                groupIds.forEach { groupId ->
                    // Delete all messages in the group first
                    val messages = scheduledMessageRepo.getScheduledMessages()
                        .where()
                        .equalTo("groupId", groupId)
                        .findAll()
                        .createSnapshot()  // Create snapshot to avoid Realm threading issues
                        .map { it.id }

                    scheduledMessageRepo.deleteScheduledMessages(messages)

                    // Then delete the group itself
                    scheduledGroupRepo.deleteScheduledGroup(groupId)
                }
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .autoDisposable(view.scope())
            .subscribe {
                // Clear selection on main thread
                view.clearSelection()
            }

        // Handle back press or home button
        view.optionsItemIntent
            .filter { it == android.R.id.home }
            .map { }
            .mergeWith(view.backPressedIntent)
            .withLatestFrom(state) { _, state -> state }
            .autoDisposable(view.scope())
            .subscribe {
                when {
                    (it.selectedGroups > 0) -> view.clearSelection()
                    else -> view.finishActivity()
                }
            }
    }
}
