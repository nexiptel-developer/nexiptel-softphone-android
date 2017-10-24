package org.linphone.chat;

/*
ChatListFragment.java
Copyright (C) 2017  Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

import android.app.Dialog;
import android.app.Fragment;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.linphone.contacts.ContactsManager;
import org.linphone.receivers.ContactsUpdatedListener;
import org.linphone.fragments.FragmentsAvailable;
import org.linphone.activities.LinphoneActivity;
import org.linphone.contacts.LinphoneContact;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.core.Address;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.Core;
import org.linphone.core.Factory;
import org.linphone.core.CoreListenerStub;

import java.util.List;

import static org.linphone.fragments.FragmentsAvailable.CHAT_LIST;

public class ChatListFragment extends Fragment implements OnClickListener, OnItemClickListener, ContactsUpdatedListener, ChatUpdatedListener {
	private LayoutInflater mInflater;
	private List<String> mConversations;
	private ListView chatList;
	private TextView noChatHistory;
	private ImageView edit, selectAll, deselectAll, delete, newDiscussion, cancel, backInCall;
	private LinearLayout editList, topbar;
	private boolean isEditMode = false;
	private CoreListenerStub mListener;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		mInflater = inflater;

		View view = inflater.inflate(R.layout.chatlist, container, false);
		chatList = (ListView) view.findViewById(R.id.chatList);
		chatList.setOnItemClickListener(this);
		registerForContextMenu(chatList);

		noChatHistory = (TextView) view.findViewById(R.id.noChatHistory);

		editList = (LinearLayout) view.findViewById(R.id.edit_list);
		topbar = (LinearLayout) view.findViewById(R.id.top_bar);

		cancel = (ImageView) view.findViewById(R.id.cancel);
		cancel.setOnClickListener(this);

		edit = (ImageView) view.findViewById(R.id.edit);
		edit.setOnClickListener(this);

		newDiscussion = (ImageView) view.findViewById(R.id.new_discussion);
		newDiscussion.setOnClickListener(this);

		selectAll = (ImageView) view.findViewById(R.id.select_all);
		selectAll.setOnClickListener(this);

		deselectAll = (ImageView) view.findViewById(R.id.deselect_all);
		deselectAll.setOnClickListener(this);

		backInCall = (ImageView) view.findViewById(R.id.back_in_call);
		backInCall.setOnClickListener(this);

		delete = (ImageView) view.findViewById(R.id.delete);
		delete.setOnClickListener(this);

		mListener = new CoreListenerStub() {
			@Override
			public void onMessageReceived(Core lc, ChatRoom cr, ChatMessage message) {
				refresh();
			}
		};

		ChatFragment.createIfNotExist();
		ChatFragment.addChatListener(this);
		return view;
	}

	private void selectAllList(boolean isSelectAll){
		int size = chatList.getAdapter().getCount();
		for(int i=0; i<size; i++) {
			chatList.setItemChecked(i,isSelectAll);
		}
	}

	private void removeChatsConversation() {
		int size = chatList.getAdapter().getCount();
		for (int i = 0; i < size; i++) {
			if (chatList.isItemChecked(i)) {
				String sipUri = chatList.getAdapter().getItem(i).toString();
				if (sipUri != null) {
					ChatRoom chatroom = LinphoneManager.getLc().getChatRoomFromUri(sipUri);
					if (chatroom != null) {
						chatroom.deleteHistory();
					}
				}
			}
		}
		quitEditMode();
		LinphoneActivity.instance().updateMissedChatCount();
	}

	public void quitEditMode(){
		isEditMode = false;
		editList.setVisibility(View.GONE);
		topbar.setVisibility(View.VISIBLE);
		refresh();
		if(getResources().getBoolean(R.bool.isTablet)){
			displayFirstChat();
		}
	}

	public int getNbItemsChecked(){
		int size = chatList.getAdapter().getCount();
		int nb = 0;
		for(int i=0; i<size; i++) {
			if(chatList.isItemChecked(i)) {
				nb ++;
			}
		}
		return nb;
	}

	public void enabledDeleteButton(Boolean enabled){
		if(enabled){
			delete.setEnabled(true);
		} else {
			if (getNbItemsChecked() == 0){
				delete.setEnabled(false);
			}
		}
	}

	private void hideAndDisplayMessageIfNoChat() {
		if (mConversations.size() == 0) {
			noChatHistory.setVisibility(View.VISIBLE);
			chatList.setVisibility(View.GONE);
			edit.setEnabled(false);
		} else {
			noChatHistory.setVisibility(View.GONE);
			chatList.setVisibility(View.VISIBLE);
			chatList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
			chatList.setAdapter(new ChatListAdapter());
			edit.setEnabled(true);
		}
	}

	public void refresh() {
		mConversations = LinphoneActivity.instance().getChatList();
		hideAndDisplayMessageIfNoChat();
	}

	public void displayFirstChat(){
		if (mConversations != null && mConversations.size() > 0) {
			LinphoneActivity.instance().displayChat(mConversations.get(0), null, null);
		} else {
			LinphoneActivity.instance().displayEmptyFragment();
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		ContactsManager.addContactsListener(this);

		if (LinphoneManager.getLc().getCallsNb() > 0) {
			backInCall.setVisibility(View.VISIBLE);
		} else {
			backInCall.setVisibility(View.INVISIBLE);
		}

		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().selectMenu(FragmentsAvailable.CHAT_LIST);
			LinphoneActivity.instance().hideTabBar(false);
		}

		Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.addListener(mListener);
		}

		refresh();
	}

	@Override
	public void onPause() {
		Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.removeListener(mListener);
		}
		ContactsManager.removeContactsListener(this);
		super.onPause();
	}

	@Override
	public void onContactsUpdated() {
		if (!LinphoneActivity.isInstanciated() || LinphoneActivity.instance().getCurrentFragment() != CHAT_LIST)
			return;

		ChatListAdapter adapter = (ChatListAdapter)chatList.getAdapter();
		if (adapter != null) {
			adapter.notifyDataSetChanged();
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		menu.add(0, v.getId(), 0, getString(R.string.delete));
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		if (info == null || info.targetView == null) {
			return false;
		}
		String sipUri = chatList.getAdapter().getItem(info.position).toString();

		LinphoneActivity.instance().removeFromChatList(sipUri);
		mConversations = LinphoneActivity.instance().getChatList();
        if (getResources().getBoolean(R.bool.isTablet)) {
			quitEditMode();
        }
		hideAndDisplayMessageIfNoChat();
		return true;
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();

		if (id == R.id.back_in_call) {
			LinphoneActivity.instance().resetClassicMenuLayoutAndGoBackToCallIfStillRunning();
			return;
		}

		if (id == R.id.select_all) {
			deselectAll.setVisibility(View.VISIBLE);
			selectAll.setVisibility(View.GONE);
			enabledDeleteButton(true);
			selectAllList(true);
			return;
		}
		if (id == R.id.deselect_all) {
			deselectAll.setVisibility(View.GONE);
			selectAll.setVisibility(View.VISIBLE);
			enabledDeleteButton(false);
			selectAllList(false);
			return;
		}

		if (id == R.id.cancel) {
			quitEditMode();
			return;
		}

		if (id == R.id.delete) {
			final Dialog dialog = LinphoneActivity.instance().displayDialog(getString(R.string.delete_text));
			Button delete = (Button) dialog.findViewById(R.id.delete_button);
			Button cancel = (Button) dialog.findViewById(R.id.cancel);

			delete.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					removeChatsConversation();
					dialog.dismiss();
					quitEditMode();
				}
			});

			cancel.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					dialog.dismiss();
					quitEditMode();
				}
			});
			dialog.show();
			return;
		}
		else if (id == R.id.edit) {
			topbar.setVisibility(View.GONE);
			editList.setVisibility(View.VISIBLE);
			isEditMode = true;
			hideAndDisplayMessageIfNoChat();
			enabledDeleteButton(false);
		}
		else if (id == R.id.new_discussion) {
			LinphoneActivity.instance().goToChatCreator(null);
			/*String sipUri = fastNewChat.getText().toString();
			if (sipUri.equals("")) {
				LinphoneActivity.instance().displayContacts(true);
			} else {
				if (!LinphoneUtils.isSipAddress(sipUri)) {
					if (LinphoneManager.getLc().getDefaultProxyConfig() == null) {
						return;
					}
					sipUri = sipUri + "@" + LinphoneManager.getLc().getDefaultProxyConfig().getDomain();
				}
				if (!LinphoneUtils.isStrictSipAddress(sipUri)) {
					sipUri = "sip:" + sipUri;
				}

			}*/
		}
	}

	@Override
	public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
		String sipUri = chatList.getAdapter().getItem(position).toString();

		if (LinphoneActivity.isInstanciated() && !isEditMode) {
			LinphoneActivity.instance().displayChat(sipUri, null, null);
		}
	}

	@Override
	public void onChatUpdated() {
		refresh();
	}

	class ChatListAdapter extends BaseAdapter {
		private class ViewHolder {
			public TextView lastMessageView;
			public TextView date;
			public TextView displayName;
			public TextView unreadMessages;
			public CheckBox select;
			public ImageView contactPicture;

			public ViewHolder(View view) {
				lastMessageView = (TextView) view.findViewById(R.id.lastMessage);
				date = (TextView) view.findViewById(R.id.date);
				displayName = (TextView) view.findViewById(R.id.sipUri);
				unreadMessages = (TextView) view.findViewById(R.id.unreadMessages);
				select = (CheckBox) view.findViewById(R.id.delete_chatroom);
				contactPicture = (ImageView) view.findViewById(R.id.contact_picture);
			}
		}

		ChatListAdapter() {}

		public int getCount() {
			return mConversations.size();
		}

		public Object getItem(int position) {
			return mConversations.get(position);
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(final int position, View convertView, ViewGroup parent) {
			View view = null;
			ViewHolder holder = null;
			String sipUri = mConversations.get(position);

			if (convertView != null) {
				view = convertView;
				holder = (ViewHolder) view.getTag();
			} else {
				view = mInflater.inflate(R.layout.chatlist_cell, parent, false);
				holder = new ViewHolder(view);
				view.setTag(holder);
			}

			Address address;
			address = Factory.instance().createAddress(sipUri);

			LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(address);
			String message = "";
			Long time;

			ChatRoom chatRoom = LinphoneManager.getLc().getChatRoom(address);
			int unreadMessagesCount = chatRoom.getUnreadMessagesCount();
			ChatMessage[] history = chatRoom.getHistory(1);
			ChatMessage msg = history[0];

			if (msg.getFileTransferInformation() != null || msg.getExternalBodyUrl() != null || msg.getAppdata() != null) {
				holder.lastMessageView.setBackgroundResource(R.drawable.chat_file_message);
				time = msg.getTime();
				holder.date.setText(LinphoneUtils.timestampToHumanDate(getActivity(),time,getString(R.string.messages_list_date_format)));
				holder.lastMessageView.setText("");
			} else if (msg.getText() != null && msg.getText().length() > 0 ){
				message = msg.getText();
				holder.lastMessageView.setBackgroundResource(0);
				time = msg.getTime();
				holder.date.setText(LinphoneUtils.timestampToHumanDate(getActivity(),time,getString(R.string.messages_list_date_format)));
				holder.lastMessageView.setText(message);
			}

			holder.displayName.setSelected(true); // For animation

			if (chatRoom.getNbParticipants() > 1) {
				holder.displayName.setText(chatRoom.getSubject());
				holder.contactPicture.setImageResource(R.drawable.chat_group_avatar);
			} else {
				if (contact != null) {
					holder.displayName.setText(contact.getFullName());
					LinphoneUtils.setThumbnailPictureFromUri(LinphoneActivity.instance(), holder.contactPicture, contact.getThumbnailUri());
				} else {
					holder.displayName.setText(LinphoneUtils.getAddressDisplayName(address));
					holder.contactPicture.setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());
				}
			}

			if (unreadMessagesCount > 0) {
				holder.unreadMessages.setVisibility(View.VISIBLE);
				holder.unreadMessages.setText(String.valueOf(unreadMessagesCount));
				if (unreadMessagesCount > 99) {
					holder.unreadMessages.setTextSize(12);
				}
				holder.displayName.setTypeface(null, Typeface.BOLD);
			} else {
				holder.unreadMessages.setVisibility(View.GONE);
				holder.displayName.setTypeface(null, Typeface.NORMAL);
			}

			if (isEditMode) {
				holder.unreadMessages.setVisibility(View.GONE);
				holder.select.setVisibility(View.VISIBLE);
				holder.select.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
						chatList.setItemChecked(position, b);
						if (getNbItemsChecked() == getCount()) {
							deselectAll.setVisibility(View.VISIBLE);
							selectAll.setVisibility(View.GONE);
							enabledDeleteButton(true);
						} else {
							if (getNbItemsChecked() == 0) {
								deselectAll.setVisibility(View.GONE);
								selectAll.setVisibility(View.VISIBLE);
								enabledDeleteButton(false);
							} else {
								deselectAll.setVisibility(View.GONE);
								selectAll.setVisibility(View.VISIBLE);
								enabledDeleteButton(true);
							}
						}
					}
				});
				if (chatList.isItemChecked(position)) {
					holder.select.setChecked(true);
				} else {
					holder.select.setChecked(false);
				}
			} else {
				if (unreadMessagesCount > 0) {
					holder.unreadMessages.setVisibility(View.VISIBLE);
				}
			}
			return view;
		}
	}
}


