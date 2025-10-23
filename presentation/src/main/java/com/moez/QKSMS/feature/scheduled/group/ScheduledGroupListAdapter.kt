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
import dev.octoshrimpy.quik.model.ScheduledMessage
import dev.octoshrimpy.quik.repository.ScheduledMessageRepository
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import io.realm.OrderedRealmCollection
import io.realm.OrderedRealmCollectionChangeListener
import io.realm.RealmResults
import kotlinx.android.synthetic.main.scheduled_group_list_item.view.*
import javax.inject.Inject

class ScheduledGroupListAdapter @Inject constructor(
    private val scheduledMessageRepo: ScheduledMessageRepository
) : QkRealmAdapter<ScheduledGroup, QkViewHolder>() {

    val groupClicks: Subject<Long> = PublishSubject.create()

    // Cache for sorted group indices and message counts
    private var sortedIndices: List<Int> = emptyList()
    private val groupStats = mutableMapOf<Long, GroupStats>()  // groupId -> stats
    private var scheduledMessages: RealmResults<ScheduledMessage>? = null
    private val scheduledMessagesListener =
        OrderedRealmCollectionChangeListener<RealmResults<ScheduledMessage>> { _, _ ->
            recalculateGroupStats()
            notifyDataSetChanged()
        }

    data class GroupStats(
        val totalCount: Int,
        val completedCount: Int,
        val pendingCount: Int,
        val isAllCompleted: Boolean
    )

    private data class MutableGroupStats(var total: Int = 0, var completed: Int = 0)

    private fun recalculateGroupStats() {
        val groups = data

        if (groups == null || !groups.isLoaded) {
            sortedIndices = emptyList()
            groupStats.clear()
            return
        }

        val statsAccumulator = mutableMapOf<Long, MutableGroupStats>()

        val messages = scheduledMessages
        if (messages != null && messages.isValid) {
            messages.forEach { message ->
                val groupId = message.groupId
                if (groupId != 0L) {
                    val stats = statsAccumulator.getOrPut(groupId) { MutableGroupStats() }
                    stats.total += 1
                    if (message.completed) {
                        stats.completed += 1
                    }
                }
            }
        }

        groupStats.clear()

        val indexedGroups = groups.indices.mapNotNull { index ->
            val group = groups[index] ?: return@mapNotNull null
            val counts = statsAccumulator[group.id] ?: MutableGroupStats()
            val total = counts.total
            val completed = counts.completed
            val pending = total - completed
            val isCompleted = total > 0 && pending == 0

            groupStats[group.id] = GroupStats(total, completed, pending, isCompleted)

            Triple(index, group, isCompleted)
        }

        // Sort: incomplete groups first (newest first), then completed groups (newest first)
        sortedIndices = indexedGroups.sortedWith(
            compareBy<Triple<Int, ScheduledGroup, Boolean>> { it.third }  // false (incomplete) first
                .thenByDescending { it.second.createdAt }  // newest first
        ).map { it.first }
    }

    override fun updateData(data: OrderedRealmCollection<ScheduledGroup>?) {
        super.updateData(data)
        recalculateGroupStats()
        notifyDataSetChanged()
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

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        // Initialize scheduledMessages when attached to RecyclerView
        if (scheduledMessages == null) {
            scheduledMessages = scheduledMessageRepo.getScheduledMessages()
        }
        scheduledMessages?.addChangeListener(scheduledMessagesListener)
        recalculateGroupStats()
        notifyDataSetChanged()
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        scheduledMessages?.removeChangeListener(scheduledMessagesListener)
        super.onDetachedFromRecyclerView(recyclerView)
    }
}
