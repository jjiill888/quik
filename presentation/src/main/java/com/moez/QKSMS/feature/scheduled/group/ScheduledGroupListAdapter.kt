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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkRealmAdapter
import dev.octoshrimpy.quik.common.base.QkViewHolder
import dev.octoshrimpy.quik.model.ScheduledGroup
import dev.octoshrimpy.quik.repository.ScheduledMessageRepository
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.android.synthetic.main.scheduled_group_list_item.view.*
import javax.inject.Inject

class ScheduledGroupListAdapter @Inject constructor(
    private val scheduledMessageRepo: ScheduledMessageRepository
) : QkRealmAdapter<ScheduledGroup, QkViewHolder>() {

    val groupClicks: Subject<Long> = PublishSubject.create()

    // Cache for sorted group indices and message counts
    private var sortedIndices: List<Int> = emptyList()
    private val groupStats = mutableMapOf<Long, GroupStats>()  // groupId -> stats

    data class GroupStats(
        val totalCount: Int,
        val completedCount: Int,
        val pendingCount: Int,
        val isAllCompleted: Boolean
    )

    private fun updateSortedIndices() {
        val groups = data ?: return
        groupStats.clear()

        // Create list of (index, group, isCompleted) tuples with cached stats
        val indexedGroups = groups.indices.map { index ->
            val group = groups[index]!!

            // Query messages once and create snapshot to avoid memory issues
            val messages = scheduledMessageRepo.getScheduledMessages()
                .where()
                .equalTo("groupId", group.id)
                .findAll()
                .createSnapshot()

            val totalCount = messages.size
            val completedCount = messages.count { it.completed }
            val pendingCount = totalCount - completedCount
            val isCompleted = totalCount > 0 && completedCount == totalCount

            // Cache stats for this group
            groupStats[group.id] = GroupStats(totalCount, completedCount, pendingCount, isCompleted)

            Triple(index, group, isCompleted)
        }

        // Sort: incomplete groups first (newest first), then completed groups (newest first)
        sortedIndices = indexedGroups.sortedWith(
            compareBy<Triple<Int, ScheduledGroup, Boolean>> { it.third }  // false (incomplete) first
                .thenByDescending { it.second.createdAt }  // newest first
        ).map { it.first }
    }

    override fun updateData(data: io.realm.OrderedRealmCollection<ScheduledGroup>?) {
        super.updateData(data)
        updateSortedIndices()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.scheduled_group_list_item, parent, false)
        return QkViewHolder(view).apply {
            view.setOnClickListener {
                // Map display position to actual data position
                val actualPosition = sortedIndices.getOrNull(adapterPosition) ?: adapterPosition
                val group = data?.get(actualPosition)
                group?.let {
                    // If in selection mode, toggle selection
                    if (toggleSelection(it.id, force = false)) {
                        view.isActivated = isSelected(it.id)
                    } else {
                        // Not in selection mode, navigate to detail
                        groupClicks.onNext(it.id)
                    }
                }
            }

            view.setOnLongClickListener {
                // Map display position to actual data position
                val actualPosition = sortedIndices.getOrNull(adapterPosition) ?: adapterPosition
                val group = data?.get(actualPosition)
                group?.let {
                    toggleSelection(it.id)
                    view.isActivated = isSelected(it.id)
                }
                true
            }
        }
    }

    override fun onBindViewHolder(holder: QkViewHolder, position: Int) {
        // Map display position to actual data position
        val actualPosition = sortedIndices.getOrNull(position) ?: position
        val group = data?.get(actualPosition) ?: return
        val view = holder.itemView

        view.groupName.text = group.name
        view.groupDescription.text = group.description
        view.groupDescription.visibility = if (group.description.isNotEmpty()) View.VISIBLE else View.GONE

        // Update the selected/highlighted state
        view.isActivated = isSelected(group.id)

        // Get cached stats for this group (no DB query needed!)
        val stats = groupStats[group.id]

        if (stats != null) {
            // Update message count text showing total
            val messageCountText = if (stats.totalCount == 1) {
                "${stats.totalCount} message"
            } else {
                "${stats.totalCount} messages"
            }
            view.messageCount.text = messageCountText

            // Update status: show "X completed, Y pending" or just "X pending"
            val statusText = when {
                stats.completedCount > 0 && stats.pendingCount > 0 -> {
                    "${stats.completedCount} completed, ${stats.pendingCount} pending"
                }
                stats.pendingCount > 0 -> {
                    if (stats.pendingCount == 1) "${stats.pendingCount} pending"
                    else "${stats.pendingCount} pending"
                }
                stats.completedCount > 0 -> {
                    "${stats.completedCount} completed"
                }
                else -> ""
            }
            view.pendingCount.text = statusText
            view.pendingCount.visibility = if (statusText.isNotEmpty()) View.VISIBLE else View.GONE
        } else {
            // Fallback if stats not available (shouldn't happen)
            view.messageCount.text = "0 messages"
            view.pendingCount.visibility = View.GONE
        }
    }

    override fun getItemId(position: Int): Long {
        val actualPosition = sortedIndices.getOrNull(position) ?: position
        return data?.get(actualPosition)?.id ?: -1
    }
}
