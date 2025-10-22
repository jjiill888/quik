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
import dev.octoshrimpy.quik.common.Navigator
import dev.octoshrimpy.quik.common.base.QkViewModel
import dev.octoshrimpy.quik.repository.ScheduledGroupRepository
import javax.inject.Inject

class ScheduledGroupListViewModel @Inject constructor(
    private val navigator: Navigator,
    private val scheduledGroupRepo: ScheduledGroupRepository
) : QkViewModel<ScheduledGroupListView, ScheduledGroupListState>(ScheduledGroupListState()) {

    override fun bindView(view: ScheduledGroupListView) {
        super.bindView(view)

        // Load all scheduled groups
        val groups = scheduledGroupRepo.getScheduledGroups()
        newState { copy(groups = groups) }

        // Navigate to group detail when clicked
        view.groupClicks
            .autoDisposable(view.scope())
            .subscribe { groupId -> navigator.showScheduledGroupDetail(groupId) }

        // Navigate to create group
        view.createGroupIntent
            .autoDisposable(view.scope())
            .subscribe { navigator.showScheduledGroupCreate() }

        // Handle back press
        view.backPressedIntent
            .autoDisposable(view.scope())
            .subscribe { view.finishActivity() }
    }
}
