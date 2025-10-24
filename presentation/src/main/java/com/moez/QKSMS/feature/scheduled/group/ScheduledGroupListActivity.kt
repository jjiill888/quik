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
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
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
import kotlinx.android.synthetic.main.main_activity.toolbar
import kotlinx.android.synthetic.main.scheduled_group_list_activity.*
import javax.inject.Inject

class ScheduledGroupListActivity : QkThemedActivity(), ScheduledGroupListView {

    @Inject lateinit var adapter: ScheduledGroupListAdapter
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    override val groupClicks by lazy { adapter.groupClicks }
    override val groupsSelectedIntent by lazy { adapter.selectionChanges }
    override val createGroupIntent by lazy { fab.clicks() }
    override val optionsItemIntent: Subject<Int> = PublishSubject.create()
    override val deleteScheduledGroups: Subject<List<Long>> = PublishSubject.create()
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

    override fun onResume() {
        super.onResume()
        // Force adapter to refresh message counts when returning from other activities
        // This ensures counts are updated after adding/completing tasks in other screens
        adapter.refreshGroupStats()
    }

    override fun render(state: ScheduledGroupListState) {
        adapter.updateData(state.groups)

        setTitle(when {
            (state.selectedGroups > 0) ->
                getString(R.string.compose_title_selected, state.selectedGroups)
            else -> getString(R.string.scheduled_groups_title)
        })

        // show/hide menu items
        toolbar.menu.findItem(R.id.delete)?.isVisible =
            (state.selectedGroups > 0)
    }

    override fun onBackPressed() = backPressedIntent.onNext(Unit)

    override fun clearSelection() = adapter.clearSelection()

    override fun showDeleteDialog(groups: List<Long>) {
        val count = groups.size
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(resources.getQuantityString(R.plurals.dialog_delete_group, count, count))
            .setPositiveButton(R.string.button_delete) { _, _ -> deleteScheduledGroups.onNext(groups) }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.scheduled_groups, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        optionsItemIntent.onNext(item.itemId)
        return true
    }

    override fun finishActivity() {
        finish()
    }
}
