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

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.widget.textChanges
import dagger.android.AndroidInjection
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.Navigator
import dev.octoshrimpy.quik.common.base.QkThemedActivity
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.android.synthetic.main.scheduled_group_create_activity.*
import javax.inject.Inject

class ScheduledGroupCreateActivity : QkThemedActivity(), ScheduledGroupCreateView {

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    @Inject lateinit var navigator: Navigator

    override val nameChanges by lazy { nameInput.textChanges() }
    override val descriptionChanges by lazy { descriptionInput.textChanges() }
    override val createIntent by lazy { createButton.clicks() }
    override val backPressedIntent: Subject<Unit> = PublishSubject.create()

    private val viewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory)[ScheduledGroupCreateViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scheduled_group_create_activity)
        setTitle(R.string.scheduled_group_create_title)
        showBackButton(true)
        viewModel.bindView(this)

        colors.theme().let { theme ->
            createButton.setBackgroundColor(theme.theme)
            createButton.setTextColor(theme.textPrimary)
        }
    }

    override fun render(state: ScheduledGroupCreateState) {
        nameInputLayout.error = state.nameError
        descriptionInputLayout.error = state.descriptionError
        createButton.isEnabled = state.canCreate && !state.creating

        // Show loading state
        createButton.text = if (state.creating) {
            getString(R.string.backup_loading)
        } else {
            getString(R.string.scheduled_group_create_button)
        }
    }

    override fun setNameError(error: String?) {
        nameInputLayout.error = error
    }

    override fun setDescriptionError(error: String?) {
        descriptionInputLayout.error = error
    }

    override fun showGroupCreated(groupId: Long) {
        navigator.showScheduledGroupDetail(groupId)
        finish()
    }

    override fun onBackPressed() {
        backPressedIntent.onNext(Unit)
    }

    override fun finishActivity() {
        finish()
    }
}
