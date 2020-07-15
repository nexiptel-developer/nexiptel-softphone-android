/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
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
package org.linphone.activities.main.contact.fragments

import android.app.Dialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.contact.adapters.ContactsListAdapter
import org.linphone.activities.main.contact.viewmodels.ContactsListViewModel
import org.linphone.activities.main.fragments.MasterFragment
import org.linphone.activities.main.navigateToContact
import org.linphone.activities.main.navigateToContactEditor
import org.linphone.activities.main.viewmodels.DialogViewModel
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.contact.Contact
import org.linphone.core.Factory
import org.linphone.core.tools.Log
import org.linphone.databinding.ContactMasterFragmentBinding
import org.linphone.utils.*

class MasterContactsFragment : MasterFragment() {
    override val dialogConfirmationMessageBeforeRemoval = R.plurals.contact_delete_dialog
    private lateinit var binding: ContactMasterFragmentBinding
    private lateinit var listViewModel: ContactsListViewModel
    private lateinit var adapter: ContactsListAdapter
    private lateinit var sharedViewModel: SharedMainViewModel

    private var sipUriToAdd: String? = null
    private var editOnClick: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ContactMasterFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this

        listViewModel = ViewModelProvider(this).get(ContactsListViewModel::class.java)
        binding.viewModel = listViewModel

        sharedViewModel = activity?.run {
            ViewModelProvider(this).get(SharedMainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        adapter = ContactsListAdapter(listSelectionViewModel)
        binding.contactsList.adapter = adapter

        binding.setEditClickListener {
            if (PermissionHelper.get().hasWriteContactsPermission()) {
                listSelectionViewModel.isEditionEnabled.value = true
            } else {
                Log.i("[Contacts] Asking for WRITE_CONTACTS permission")
                requestPermissions(arrayOf(android.Manifest.permission.WRITE_CONTACTS), 1)
            }
        }

        val layoutManager = LinearLayoutManager(activity)
        binding.contactsList.layoutManager = layoutManager

        // Swipe action
        val swipeConfiguration = RecyclerViewSwipeConfiguration()
        val white = ContextCompat.getColor(requireContext(), R.color.white_color)

        swipeConfiguration.rightToLeftAction = RecyclerViewSwipeConfiguration.Action("Delete", white, ContextCompat.getColor(requireContext(), R.color.red_color))
        val swipeListener = object : RecyclerViewSwipeListener {
            override fun onLeftToRightSwipe(viewHolder: RecyclerView.ViewHolder) {}

            override fun onRightToLeftSwipe(viewHolder: RecyclerView.ViewHolder) {
                val viewModel = DialogViewModel(getString(R.string.contact_delete_one_dialog))
                val dialog: Dialog = DialogUtils.getDialog(requireContext(), viewModel)

                viewModel.showCancelButton {
                    adapter.notifyItemChanged(viewHolder.adapterPosition)
                    dialog.dismiss()
                }

                viewModel.showDeleteButton({
                    listViewModel.deleteContact(adapter.getItemAt(viewHolder.adapterPosition))
                    dialog.dismiss()
                }, getString(R.string.dialog_delete))

                dialog.show()
            }
        }
        RecyclerViewSwipeUtils(ItemTouchHelper.LEFT, swipeConfiguration, swipeListener)
            .attachToRecyclerView(binding.contactsList)

        // Divider between items
        val dividerItemDecoration = DividerItemDecoration(context, layoutManager.orientation)
        dividerItemDecoration.setDrawable(resources.getDrawable(R.drawable.divider, null))
        binding.contactsList.addItemDecoration(dividerItemDecoration)

        // Displays the first letter header
        val headerItemDecoration = RecyclerViewHeaderDecoration(adapter)
        binding.contactsList.addItemDecoration(headerItemDecoration)

        adapter.selectedContactEvent.observe(viewLifecycleOwner, Observer {
            it.consume { contact ->
                Log.i("[Contacts] Selected item in list changed: $contact")
                sharedViewModel.selectedContact.value = contact
                listViewModel.filter.value = ""

                if (editOnClick) {
                    navigateToContactEditor(sipUriToAdd)
                    editOnClick = false
                    sipUriToAdd = null
                } else {
                    navigateToContact()
                }
            }
        })

        listViewModel.contactsList.observe(viewLifecycleOwner, Observer {
            adapter.submitList(it)
        })

        binding.setAllContactsToggleClickListener {
            listViewModel.sipContactsSelected.value = false
        }
        binding.setSipContactsToggleClickListener {
            listViewModel.sipContactsSelected.value = true
        }

        listViewModel.sipContactsSelected.observe(viewLifecycleOwner, Observer {
            listViewModel.updateContactsList()
        })

        listViewModel.filter.observe(viewLifecycleOwner, Observer {
            listViewModel.updateContactsList()
        })

        binding.setNewContactClickListener {
            // Remove any previously selected contact
            sharedViewModel.selectedContact.value = null
            navigateToContactEditor(sipUriToAdd)
        }

        val id = arguments?.getString("id")
        if (id != null) {
            Log.i("[Contacts] Found contact id parameter in arguments: $id")
            arguments?.clear()
            val contact = coreContext.contactsManager.findContactById(id)
            if (contact != null) {
                Log.i("[Contacts] Found matching contact $contact")
                adapter.selectedContactEvent.value = Event(contact)
            }
        } else {
            val sipUri = arguments?.getString("sipUri")
            if (sipUri != null) {
                Log.i("[Contacts] Found sipUri parameter in arguments: $sipUri")
                sipUriToAdd = sipUri
                arguments?.clear()

                val activity = requireActivity() as MainActivity
                activity.showSnackBar(R.string.contact_choose_existing_or_new_to_add_number)
                editOnClick = true
            } else {
                // When trying to display a non-native contact from history
                val addressString = arguments?.getString("address")
                if (addressString != null) {
                    val address = Factory.instance().createAddress(addressString)
                    if (address != null) {
                        Log.i("[Contacts] Found friend native pointer parameter in arguments: ${address.asStringUriOnly()}")
                        arguments?.clear()

                        val contact = coreContext.contactsManager.findContactByAddress(address)
                        if (contact != null) {
                            Log.i("[Contacts] Found matching contact $contact")
                            adapter.selectedContactEvent.value = Event(contact)
                        }
                    }
                }
            }
        }

        if (!PermissionHelper.get().hasReadContactsPermission()) {
            Log.i("[Contacts] Asking for READ_CONTACTS permission")
            requestPermissions(arrayOf(android.Manifest.permission.READ_CONTACTS), 0)
        }
    }

    override fun getItemCount(): Int {
        return adapter.itemCount
    }

    override fun deleteItems(indexesOfItemToDelete: ArrayList<Int>) {
        val list = ArrayList<Contact>()
        for (index in indexesOfItemToDelete) {
            val contact = adapter.getItemAt(index)
            list.add(contact)
        }
        listViewModel.deleteContacts(list)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == 0) {
            val granted = grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) {
                Log.i("[Contacts] READ_CONTACTS permission granted")
                coreContext.contactsManager.onReadContactsPermissionGranted()
                coreContext.contactsManager.fetchContactsAsync()
            } else {
                Log.w("[Contacts] READ_CONTACTS permission denied")
            }
        } else if (requestCode == 1) {
            val granted = grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) {
                Log.i("[Contacts] WRITE_CONTACTS permission granted")
                listSelectionViewModel.isEditionEnabled.value = true
            } else {
                Log.w("[Contacts] WRITE_CONTACTS permission denied")
            }
        }
    }
}
