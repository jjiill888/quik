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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.scheduled_group_list_item, parent, false)
        return QkViewHolder(view).apply {
            view.setOnClickListener {
                val group = data?.get(adapterPosition)
                group?.let { groupClicks.onNext(it.id) }
            }
        }
    }

    override fun onBindViewHolder(holder: QkViewHolder, position: Int) {
        val group = data?.get(position) ?: return
        val view = holder.itemView
        val context = view.context

        view.groupName.text = group.name
        view.groupDescription.text = group.description
        view.groupDescription.visibility = if (group.description.isNotEmpty()) View.VISIBLE else View.GONE

        // Get all scheduled messages for this group
        val allMessages = scheduledMessageRepo.getScheduledMessages()
            .where()
            .equalTo("groupId", group.id)
            .findAll()

        val totalCount = allMessages.size

        // Count pending messages (messages with date in the future)
        val now = System.currentTimeMillis()
        val pendingCount = allMessages.count { it.date > now }

        // Update message count text
        val messageCountText = if (totalCount == 1) {
            "$totalCount message"
        } else {
            "$totalCount messages"
        }
        view.messageCount.text = messageCountText

        // Update pending count text
        val pendingCountText = if (pendingCount == 1) {
            "$pendingCount pending"
        } else {
            "$pendingCount pending"
        }
        view.pendingCount.text = pendingCountText
        view.pendingCount.visibility = if (pendingCount > 0) View.VISIBLE else View.GONE
    }
}
