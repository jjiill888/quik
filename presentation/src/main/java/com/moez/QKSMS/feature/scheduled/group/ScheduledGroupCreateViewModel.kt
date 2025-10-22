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
import dev.octoshrimpy.quik.common.base.QkViewModel
import dev.octoshrimpy.quik.interactor.CreateScheduledGroup
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.withLatestFrom
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class ScheduledGroupCreateViewModel @Inject constructor(
    private val context: Context,
    private val createScheduledGroupInteractor: CreateScheduledGroup
) : QkViewModel<ScheduledGroupCreateView, ScheduledGroupCreateState>(
    ScheduledGroupCreateState()
) {

    companion object {
        private const val MAX_NAME_LENGTH = 100
        private const val MAX_DESCRIPTION_LENGTH = 500
    }

    override fun bindView(view: ScheduledGroupCreateView) {
        super.bindView(view)

        // Update state when name changes
        view.nameChanges
            .autoDisposable(view.scope())
            .subscribe { name ->
                newState {
                    copy(
                        name = name.toString(),
                        nameError = validateName(name.toString())
                    )
                }
            }

        // Update state when description changes
        view.descriptionChanges
            .autoDisposable(view.scope())
            .subscribe { description ->
                newState {
                    copy(
                        description = description.toString(),
                        descriptionError = validateDescription(description.toString())
                    )
                }
            }

        // Update canCreate flag whenever state changes
        disposables += state
            .subscribe { state ->
                val canCreate = state.name.isNotBlank() &&
                        state.nameError == null &&
                        state.descriptionError == null &&
                        !state.creating
                if (state.canCreate != canCreate) {
                    newState { copy(canCreate = canCreate) }
                }
            }

        // Handle create button click
        view.createIntent
            .withLatestFrom(state) { _, state -> state }
            .filter { it.canCreate }
            .autoDisposable(view.scope())
            .subscribe { state ->
                createGroup(state.name, state.description, view)
            }

        // Handle back press
        view.backPressedIntent
            .autoDisposable(view.scope())
            .subscribe { view.finishActivity() }
    }

    private fun validateName(name: String): String? {
        return when {
            name.isBlank() -> context.getString(R.string.scheduled_group_name_required)
            name.length > MAX_NAME_LENGTH -> context.getString(
                R.string.scheduled_group_name_too_long,
                MAX_NAME_LENGTH
            )
            else -> null
        }
    }

    private fun validateDescription(description: String): String? {
        return when {
            description.length > MAX_DESCRIPTION_LENGTH -> context.getString(
                R.string.scheduled_group_description_too_long,
                MAX_DESCRIPTION_LENGTH
            )
            else -> null
        }
    }

    private fun createGroup(name: String, description: String, view: ScheduledGroupCreateView) {
        newState { copy(creating = true) }

        disposables += createScheduledGroupInteractor
            .buildObservable(CreateScheduledGroup.Params(name, description))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { groupId ->
                    newState { copy(creating = false) }
                    view.showGroupCreated(groupId as Long)
                },
                { error ->
                    newState { copy(creating = false) }
                    // Handle error - for now just log it
                    error.printStackTrace()
                }
            )
    }
}
