/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.main.contacts.model

import android.net.Uri
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.contacts.AbstractAvatarModel
import org.linphone.contacts.getNativeContactPictureUri
import org.linphone.core.ChatRoom.SecurityLevel
import org.linphone.core.ConsolidatedPresence
import org.linphone.core.Friend
import org.linphone.core.FriendListenerStub
import org.linphone.core.tools.Log
import org.linphone.ui.main.model.isInSecureMode
import org.linphone.utils.AppUtils
import org.linphone.utils.TimestampUtils

class ContactAvatarModel @WorkerThread constructor(val friend: Friend) : AbstractAvatarModel() {
    companion object {
        private const val TAG = "[Contact Avatar Model]"
    }

    val id = friend.refKey ?: friend.name

    val contactName = friend.name

    val isFavourite = MutableLiveData<Boolean>()

    val lastPresenceInfo = MutableLiveData<String>()

    val presenceStatus = MutableLiveData<ConsolidatedPresence>()

    val name = MutableLiveData<String>()

    val firstLetter: String = AppUtils.getFirstLetter(friend.name.orEmpty())

    private val friendListener = object : FriendListenerStub() {
        @WorkerThread
        override fun onPresenceReceived(fr: Friend) {
            Log.d(
                "$TAG Presence received for friend [${fr.name}]: [${fr.consolidatedPresence}]"
            )
            computePresence()
        }
    }

    init {
        if (friend.addresses.isNotEmpty()) {
            friend.addListener(friendListener)
        }

        isFavourite.postValue(friend.starred)
        initials.postValue(AppUtils.getInitials(friend.name.orEmpty()))
        trust.postValue(SecurityLevel.Encrypted) // TODO FIXME: use API
        showTrust.postValue(coreContext.core.defaultAccount?.isInSecureMode())
        images.postValue(arrayListOf(getAvatarUri(friend).toString()))

        name.postValue(friend.name)
        computePresence()
    }

    @WorkerThread
    fun destroy() {
        if (friend.addresses.isNotEmpty()) {
            friend.removeListener(friendListener)
        }
    }

    @WorkerThread
    private fun getAvatarUri(friend: Friend): Uri? {
        val picturePath = friend.photo
        if (!picturePath.isNullOrEmpty()) {
            return Uri.parse(picturePath)
        }

        val refKey = friend.refKey
        if (refKey != null) {
            try {
                return friend.getNativeContactPictureUri()
            } catch (numberFormatException: NumberFormatException) {
                // Expected for contacts created by Linphone
            }
        }

        return null
    }

    @WorkerThread
    private fun computePresence() {
        val presence = friend.consolidatedPresence
        Log.d("$TAG Friend [${friend.name}] presence status is [$presence]")
        presenceStatus.postValue(presence)

        val presenceString = when (presence) {
            ConsolidatedPresence.Online -> {
                AppUtils.getString(R.string.friend_presence_status_online)
            }
            ConsolidatedPresence.Busy -> {
                val timestamp = friend.presenceModel?.latestActivityTimestamp ?: -1L
                if (timestamp != -1L) {
                    when {
                        TimestampUtils.isToday(timestamp) -> {
                            val time = TimestampUtils.timeToString(
                                timestamp,
                                timestampInSecs = true
                            )
                            AppUtils.getFormattedString(
                                R.string.friend_presence_status_was_online_today_at,
                                time
                            )
                        }
                        TimestampUtils.isYesterday(timestamp) -> {
                            val time = TimestampUtils.timeToString(
                                timestamp,
                                timestampInSecs = true
                            )
                            AppUtils.getFormattedString(
                                R.string.friend_presence_status_was_online_yesterday_at,
                                time
                            )
                        }
                        else -> {
                            val date = TimestampUtils.toString(
                                timestamp,
                                onlyDate = true,
                                shortDate = false,
                                hideYear = true
                            )
                            AppUtils.getFormattedString(
                                R.string.friend_presence_status_was_online_on,
                                date
                            )
                        }
                    }
                } else {
                    AppUtils.getString(R.string.friend_presence_status_away)
                }
            }
            ConsolidatedPresence.DoNotDisturb -> {
                AppUtils.getString(R.string.friend_presence_status_do_not_disturb)
            }
            else -> ""
        }
        lastPresenceInfo.postValue(presenceString)
    }
}
