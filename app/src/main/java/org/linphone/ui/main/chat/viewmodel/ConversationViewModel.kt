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
package org.linphone.ui.main.chat.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.ChatMessage
import org.linphone.core.ChatRoom
import org.linphone.core.ChatRoomListenerStub
import org.linphone.core.EventLog
import org.linphone.core.Factory
import org.linphone.core.Friend
import org.linphone.core.tools.Log
import org.linphone.ui.main.chat.model.ChatMessageModel
import org.linphone.ui.main.chat.model.EventLogModel
import org.linphone.ui.main.chat.model.FileModel
import org.linphone.ui.main.chat.model.ParticipantModel
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.FileUtils
import org.linphone.utils.LinphoneUtils

class ConversationViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Conversation ViewModel]"

        const val MAX_TIME_TO_GROUP_MESSAGES = 60 // 1 minute
        const val SCROLLING_POSITION_NOT_SET = -1
    }

    val showBackButton = MutableLiveData<Boolean>()

    val avatarModel = MutableLiveData<ContactAvatarModel>()

    val events = MutableLiveData<ArrayList<EventLogModel>>()

    val isGroup = MutableLiveData<Boolean>()

    val subject = MutableLiveData<String>()

    val isReadOnly = MutableLiveData<Boolean>()

    val composingLabel = MutableLiveData<String>()

    val textToSend = MutableLiveData<String>()

    val searchBarVisible = MutableLiveData<Boolean>()

    val searchFilter = MutableLiveData<String>()

    val isEmojiPickerOpen = MutableLiveData<Boolean>()

    val isParticipantsListOpen = MutableLiveData<Boolean>()

    val participants = MutableLiveData<ArrayList<ParticipantModel>>()

    val isFileAttachmentsListOpen = MutableLiveData<Boolean>()

    val attachments = MutableLiveData<ArrayList<FileModel>>()

    val isReplying = MutableLiveData<Boolean>()

    val isReplyingTo = MutableLiveData<String>()

    val isReplyingToMessage = MutableLiveData<String>()

    var scrollingPosition: Int = SCROLLING_POSITION_NOT_SET

    val requestKeyboardHidingEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val focusSearchBarEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val fileToDisplayEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val conferenceToJoinEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val openWebBrowserEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val emojiToAddEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val participantUsernameToAddEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val chatRoomFoundEvent = MutableLiveData<Event<Boolean>>()

    lateinit var chatRoom: ChatRoom

    private var chatMessageToReplyTo: ChatMessage? = null

    private val chatRoomListener = object : ChatRoomListenerStub() {
        @WorkerThread
        override fun onChatMessageSending(chatRoom: ChatRoom, eventLog: EventLog) {
            val message = eventLog.chatMessage
            Log.i("$TAG Chat message [$message] is being sent")

            val list = arrayListOf<EventLogModel>()
            list.addAll(events.value.orEmpty())

            val avatarModel = coreContext.contactsManager.getContactAvatarModelForAddress(
                message?.localAddress
            )
            val lastEvent = events.value.orEmpty().lastOrNull()
            val group = if (lastEvent != null) {
                shouldWeGroupTwoEvents(eventLog, lastEvent.eventLog)
            } else {
                false
            }
            list.add(
                EventLogModel(
                    eventLog,
                    avatarModel,
                    LinphoneUtils.isChatRoomAGroup(chatRoom),
                    group,
                    true,
                    { file ->
                        fileToDisplayEvent.postValue(Event(file))
                    },
                    { conferenceUri ->
                        conferenceToJoinEvent.postValue(Event(conferenceUri))
                    },
                    { url ->
                        openWebBrowserEvent.postValue(Event(url))
                    }
                )
            )

            events.postValue(list)
        }

        @WorkerThread
        override fun onChatMessageSent(chatRoom: ChatRoom, eventLog: EventLog) {
            val message = eventLog.chatMessage
            Log.i("$TAG Chat message [$message] has been sent")
        }

        @WorkerThread
        override fun onIsComposingReceived(
            chatRoom: ChatRoom,
            remoteAddress: Address,
            isComposing: Boolean
        ) {
            Log.i(
                "$TAG Remote [${remoteAddress.asStringUriOnly()}] is ${if (isComposing) "composing" else "no longer composing"}"
            )
            computeComposingLabel()
        }

        @WorkerThread
        override fun onChatMessagesReceived(chatRoom: ChatRoom, eventLogs: Array<EventLog>) {
            Log.i("$TAG Received [${eventLogs.size}] new message(s)")
            chatRoom.markAsRead()
            computeComposingLabel()

            val list = arrayListOf<EventLogModel>()
            list.addAll(events.value.orEmpty())

            val newList = getEventsListFromHistory(
                eventLogs,
                searchFilter.value.orEmpty().trim()
            )
            list.addAll(newList)

            // TODO: handle case when first one of the newly received messages should be grouped with last one of the current list

            events.postValue(list)
        }

        @WorkerThread
        override fun onParticipantAdded(chatRoom: ChatRoom, eventLog: EventLog) {
            computeParticipantsList()
        }

        @WorkerThread
        override fun onParticipantRemoved(chatRoom: ChatRoom, eventLog: EventLog) {
            computeParticipantsList()
        }
    }

    init {
        searchBarVisible.value = false
        isEmojiPickerOpen.value = false
    }

    override fun onCleared() {
        super.onCleared()

        viewModelScope.launch {
            for (file in attachments.value.orEmpty()) {
                file.deleteFile()
            }
        }

        coreContext.postOnCoreThread {
            if (::chatRoom.isInitialized) {
                chatRoom.removeListener(chatRoomListener)
            }
            events.value.orEmpty().forEach(EventLogModel::destroy)
        }
    }

    @UiThread
    fun openSearchBar() {
        searchBarVisible.value = true
        focusSearchBarEvent.value = Event(true)
    }

    @UiThread
    fun closeSearchBar() {
        clearFilter()
        searchBarVisible.value = false
        focusSearchBarEvent.value = Event(false)
    }

    @UiThread
    fun clearFilter() {
        searchFilter.value = ""
    }

    @UiThread
    fun findChatRoom(room: ChatRoom?, localSipUri: String, remoteSipUri: String) {
        coreContext.postOnCoreThread { core ->
            Log.i(
                "$TAG Looking for chat room with local SIP URI [$localSipUri] and remote SIP URI [$remoteSipUri]"
            )
            if (room != null && ::chatRoom.isInitialized && chatRoom == room) {
                Log.i("$TAG Chat room object already in memory, skipping")
                chatRoomFoundEvent.postValue(Event(true))
                return@postOnCoreThread
            }

            val localAddress = Factory.instance().createAddress(localSipUri)
            val remoteAddress = Factory.instance().createAddress(remoteSipUri)

            if (room != null && (!::chatRoom.isInitialized || chatRoom != room)) {
                if (localAddress?.weakEqual(room.localAddress) == true && remoteAddress?.weakEqual(
                        room.peerAddress
                    ) == true
                ) {
                    Log.i("$TAG Chat room object available in sharedViewModel, using it")
                    chatRoom = room
                    chatRoom.addListener(chatRoomListener)
                    configureChatRoom()
                    chatRoomFoundEvent.postValue(Event(true))
                    return@postOnCoreThread
                }
            }

            if (localAddress != null && remoteAddress != null) {
                Log.i("$TAG Searching for chat room in Core using local & peer SIP addresses")
                val found = core.searchChatRoom(
                    null,
                    localAddress,
                    remoteAddress,
                    arrayOfNulls(
                        0
                    )
                )
                if (found != null) {
                    chatRoom = found
                    chatRoom.addListener(chatRoomListener)

                    configureChatRoom()
                    chatRoomFoundEvent.postValue(Event(true))
                } else {
                    Log.e("$TAG Failed to find chat room given local & remote addresses!")
                    chatRoomFoundEvent.postValue(Event(false))
                }
            } else {
                Log.e("$TAG Failed to parse local or remote SIP URI as Address!")
                chatRoomFoundEvent.postValue(Event(false))
            }
        }
    }

    @UiThread
    fun applyFilter(filter: String) {
        coreContext.postOnCoreThread {
            computeEvents(filter)
        }
    }

    @UiThread
    fun toggleEmojiPickerVisibility() {
        isEmojiPickerOpen.value = isEmojiPickerOpen.value == false
        if (isEmojiPickerOpen.value == true) {
            requestKeyboardHidingEvent.value = Event(true)
        }
    }

    @UiThread
    fun insertEmoji(emoji: String) {
        emojiToAddEvent.value = Event(emoji)
    }

    @UiThread
    fun replyToMessage(model: ChatMessageModel) {
        coreContext.postOnCoreThread {
            val message = model.chatMessage
            Log.i("$TAG Pending reply to chat message [${message.messageId}]")
            chatMessageToReplyTo = message
            isReplyingTo.postValue(model.avatarModel.friend.name)
            isReplyingToMessage.postValue(LinphoneUtils.getTextDescribingMessage(message))
            isReplying.postValue(true)
        }
    }

    @UiThread
    fun cancelReply() {
        Log.i("$TAG Cancelling reply")
        isReplying.value = false
        chatMessageToReplyTo = null
    }

    @UiThread
    fun sendMessage() {
        coreContext.postOnCoreThread {
            val messageToReplyTo = chatMessageToReplyTo
            val message = if (messageToReplyTo != null) {
                Log.i("$TAG Sending message as reply to [${messageToReplyTo.messageId}]")
                chatRoom.createReplyMessage(messageToReplyTo)
            } else {
                chatRoom.createEmptyMessage()
            }

            val toSend = textToSend.value.orEmpty().trim()
            if (toSend.isNotEmpty()) {
                message.addUtf8TextContent(toSend)
            }

            for (attachment in attachments.value.orEmpty()) {
                val content = Factory.instance().createContent()

                content.type = when (attachment.mimeType) {
                    FileUtils.MimeType.Image -> "image"
                    FileUtils.MimeType.Audio -> "audio"
                    FileUtils.MimeType.Video -> "video"
                    FileUtils.MimeType.Pdf -> "application"
                    FileUtils.MimeType.PlainText -> "text"
                    else -> "file"
                }
                content.subtype = if (attachment.mimeType == FileUtils.MimeType.PlainText) {
                    "plain"
                } else {
                    FileUtils.getExtensionFromFileName(attachment.fileName)
                }
                content.name = attachment.fileName
                content.filePath = attachment.file // Let the file body handler take care of the upload

                message.addFileContent(content)
            }

            if (message.contents.isNotEmpty()) {
                Log.i("$TAG Sending message")
                message.send()
            }

            Log.i("$TAG Message sent, re-setting defaults")
            textToSend.postValue("")
            isReplying.postValue(false)
            isFileAttachmentsListOpen.postValue(false)
            isParticipantsListOpen.postValue(false)
            isEmojiPickerOpen.postValue(false)

            // Warning: do not delete files
            val attachmentsList = arrayListOf<FileModel>()
            attachments.postValue(attachmentsList)

            chatMessageToReplyTo = null
        }
    }

    @UiThread
    fun deleteChatMessage(chatMessageModel: ChatMessageModel) {
        coreContext.postOnCoreThread {
            val eventsLogs = events.value.orEmpty()
            val found = eventsLogs.find {
                it.model == chatMessageModel
            }
            if (found != null) {
                val list = arrayListOf<EventLogModel>()
                list.addAll(eventsLogs)
                list.remove(found)
                events.postValue(list)
            }

            Log.i("$TAG Deleting message id [${chatMessageModel.id}]")
            chatRoom.deleteMessage(chatMessageModel.chatMessage)
        }
    }

    @UiThread
    fun closeParticipantsList() {
        isParticipantsListOpen.value = false
    }

    @UiThread
    fun closeFileAttachmentsList() {
        viewModelScope.launch {
            for (file in attachments.value.orEmpty()) {
                file.deleteFile()
            }
        }
        val list = arrayListOf<FileModel>()
        attachments.value = list

        isFileAttachmentsListOpen.value = false
    }

    @UiThread
    fun addAttachment(file: String) {
        val list = arrayListOf<FileModel>()
        list.addAll(attachments.value.orEmpty())
        val model = FileModel(file) { file ->
            removeAttachment(file)
        }
        list.add(model)
        attachments.value = list

        if (list.isNotEmpty()) {
            isFileAttachmentsListOpen.value = true
        }
    }

    @UiThread
    fun removeAttachment(file: String, delete: Boolean = true) {
        val list = arrayListOf<FileModel>()
        list.addAll(attachments.value.orEmpty())
        val found = list.find {
            it.file == file
        }
        if (found != null) {
            if (delete) {
                viewModelScope.launch {
                    found.deleteFile()
                }
            }
            list.remove(found)
        } else {
            Log.w("$TAG Failed to find file attachment matching [$file]")
        }
        attachments.value = list

        if (list.isEmpty()) {
            isFileAttachmentsListOpen.value = false
        }
    }

    @WorkerThread
    private fun configureChatRoom() {
        scrollingPosition = SCROLLING_POSITION_NOT_SET
        computeComposingLabel()

        val empty = chatRoom.hasCapability(ChatRoom.Capabilities.Conference.toInt()) && chatRoom.participants.isEmpty()
        val readOnly = chatRoom.isReadOnly || empty
        isReadOnly.postValue(readOnly)
        if (readOnly) {
            Log.w("$TAG Chat room with subject [${chatRoom.subject}] is read only!")
        }

        val group = LinphoneUtils.isChatRoomAGroup(chatRoom)
        isGroup.postValue(group)

        subject.postValue(chatRoom.subject)

        val friends = arrayListOf<Friend>()
        val address = if (chatRoom.hasCapability(ChatRoom.Capabilities.Basic.toInt())) {
            chatRoom.peerAddress
        } else {
            for (participant in chatRoom.participants) {
                val friend = coreContext.contactsManager.findContactByAddress(participant.address)
                if (friend != null && !friends.contains(friend)) {
                    friends.add(friend)
                }
            }

            val firstParticipant = chatRoom.participants.firstOrNull()
            firstParticipant?.address ?: chatRoom.peerAddress
        }

        val avatar = if (group) {
            val fakeFriend = coreContext.core.createFriend()
            val model = ContactAvatarModel(fakeFriend)
            model.setPicturesFromFriends(friends)
            model
        } else {
            coreContext.contactsManager.getContactAvatarModelForAddress(address)
        }
        avatarModel.postValue(avatar)

        computeEvents()
        chatRoom.markAsRead()
        computeParticipantsList()
    }

    @WorkerThread
    private fun computeEvents(filter: String = "") {
        events.value.orEmpty().forEach(EventLogModel::destroy)

        val history = chatRoom.getHistoryEvents(0)
        val eventsList = getEventsListFromHistory(history, filter)
        events.postValue(eventsList)
    }

    @WorkerThread
    private fun processGroupedEvents(
        groupedEventLogs: ArrayList<EventLog>
    ): ArrayList<EventLogModel> {
        val groupChatRoom = LinphoneUtils.isChatRoomAGroup(chatRoom)
        val eventsList = arrayListOf<EventLogModel>()

        // Handle all events in group, then re-start a new group with current item
        var index = 0
        for (groupedEvent in groupedEventLogs) {
            val avatar = coreContext.contactsManager.getContactAvatarModelForAddress(
                groupedEvent.chatMessage?.fromAddress
            )
            val model = EventLogModel(
                groupedEvent,
                avatar,
                groupChatRoom,
                index > 0,
                index == groupedEventLogs.size - 1,
                { file ->
                    fileToDisplayEvent.postValue(Event(file))
                },
                { conferenceUri ->
                    conferenceToJoinEvent.postValue(Event(conferenceUri))
                },
                { url ->
                    openWebBrowserEvent.postValue(Event(url))
                }
            )
            eventsList.add(model)

            index += 1
        }

        return eventsList
    }

    @WorkerThread
    private fun getEventsListFromHistory(history: Array<EventLog>, filter: String = ""): ArrayList<EventLogModel> {
        val eventsList = arrayListOf<EventLogModel>()
        val groupedEventLogs = arrayListOf<EventLog>()
        for (event in history) {
            if (filter.isNotEmpty()) {
                if (event.type == EventLog.Type.ConferenceChatMessage) {
                    val message = event.chatMessage ?: continue
                    val fromAddress = message.fromAddress
                    val model = coreContext.contactsManager.getContactAvatarModelForAddress(
                        fromAddress
                    )
                    if (
                        !model.name.value.orEmpty().contains(filter, ignoreCase = true) &&
                        !fromAddress.asStringUriOnly().contains(filter, ignoreCase = true) &&
                        !message.utf8Text.orEmpty().contains(filter, ignoreCase = true)
                    ) {
                        continue
                    }
                } else {
                    continue
                }
            }

            if (groupedEventLogs.isEmpty()) {
                groupedEventLogs.add(event)
                continue
            }

            val previousGroupEvent = groupedEventLogs.last()
            val groupEvents = shouldWeGroupTwoEvents(event, previousGroupEvent)

            if (!groupEvents) {
                eventsList.addAll(processGroupedEvents(groupedEventLogs))
                groupedEventLogs.clear()
            }

            groupedEventLogs.add(event)
        }

        if (groupedEventLogs.isNotEmpty()) {
            eventsList.addAll(processGroupedEvents(groupedEventLogs))
            groupedEventLogs.clear()
        }

        return eventsList
    }

    @WorkerThread
    private fun shouldWeGroupTwoEvents(event: EventLog, previousGroupEvent: EventLog): Boolean {
        return if (previousGroupEvent.type == EventLog.Type.ConferenceChatMessage && event.type == EventLog.Type.ConferenceChatMessage) {
            val previousChatMessage = previousGroupEvent.chatMessage!!
            val chatMessage = event.chatMessage!!

            // If they have the same direction, the same from address and were sent in a short timelapse, group them
            chatMessage.isOutgoing == previousChatMessage.isOutgoing &&
                chatMessage.fromAddress.weakEqual(previousChatMessage.fromAddress) &&
                kotlin.math.abs(chatMessage.time - previousChatMessage.time) < MAX_TIME_TO_GROUP_MESSAGES
        } else {
            false
        }
    }

    @WorkerThread
    private fun computeComposingLabel() {
        val composingFriends = arrayListOf<String>()
        var label = ""
        for (address in chatRoom.composingAddresses) {
            val avatar = coreContext.contactsManager.getContactAvatarModelForAddress(address)
            val name = avatar.name.value ?: LinphoneUtils.getDisplayName(address)
            composingFriends.add(name)
            label += "$name, "
        }
        if (composingFriends.size > 0) {
            label = label.dropLast(2)

            val format = AppUtils.getStringWithPlural(
                R.plurals.conversation_composing_label,
                composingFriends.size,
                label
            )
            composingLabel.postValue(format)
        } else {
            composingLabel.postValue("")
        }
    }

    @WorkerThread
    private fun computeParticipantsList() {
        val participantsList = arrayListOf<ParticipantModel>()

        for (participant in chatRoom.participants) {
            val model = ParticipantModel(participant.address, onClicked = { clicked ->
                Log.i("$TAG Clicked on participant [${clicked.sipUri}]")
                coreContext.postOnCoreThread {
                    val username = clicked.address.username
                    if (!username.isNullOrEmpty()) {
                        participantUsernameToAddEvent.postValue(Event(username))
                    }
                }
            })
            participantsList.add(model)
        }

        participants.postValue(participantsList)
    }
}
