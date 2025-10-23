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
package dev.octoshrimpy.quik.repository

import dev.octoshrimpy.quik.model.ScheduledGroup
import dev.octoshrimpy.quik.model.ScheduledMessage
import io.realm.RealmResults

interface ScheduledGroupRepository {

    /**
     * Creates a new scheduled group
     */
    fun createScheduledGroup(name: String, description: String): ScheduledGroup

    /**
     * Returns all scheduled groups
     */
    fun getScheduledGroups(): RealmResults<ScheduledGroup>

    /**
     * Returns the scheduled group with the given [id]
     */
    fun getScheduledGroup(id: Long): ScheduledGroup?

    /**
     * Returns all scheduled messages for a given [groupId]
     */
    fun getScheduledMessagesForGroup(groupId: Long): RealmResults<ScheduledMessage>

    /**
     * Updates a scheduled group
     */
    fun updateScheduledGroup(group: ScheduledGroup)

    /**
     * Deletes a scheduled group along with any scheduled messages that belong to it.
     */
    fun deleteScheduledGroup(id: Long)

}
