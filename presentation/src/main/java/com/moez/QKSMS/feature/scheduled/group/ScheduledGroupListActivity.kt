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
import androidx.recyclerview.widget.LinearLayoutManager
import com.jakewharton.rxbinding2.view.clicks
import dagger.android.AndroidInjection
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkThemedActivity
import dev.octoshrimpy.quik.common.util.extensions.setBackgroundTint
import dev.octoshrimpy.quik.common.util.extensions.setTint
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.android.synthetic.main.scheduled_group_list_activity.*
import javax.inject.Inject

class ScheduledGroupListActivity : QkThemedActivity(), ScheduledGroupListView {

    @Inject lateinit var adapter: ScheduledGroupListAdapter
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    override val groupClicks by lazy { adapter.groupClicks }
    override val createGroupIntent by lazy { fab.clicks() }
    override val backPressedIntent: Subject<Unit> = PublishSubject.create()

    private val viewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory)[ScheduledGroupListViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scheduled_group_list_activity)
        setTitle(R.string.scheduled_groups_title)
        showBackButton(true)
        viewModel.bindView(this)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        colors.theme().let { theme ->
            fab.setTint(theme.textPrimary)
            fab.setBackgroundTint(theme.theme)
        }
    }

    override fun render(state: ScheduledGroupListState) {
        adapter.updateData(state.groups)
    }

    override fun onBackPressed() = backPressedIntent.onNext(Unit)

    override fun finishActivity() {
        finish()
    }
}
