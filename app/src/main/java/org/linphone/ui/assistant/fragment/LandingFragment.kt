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
package org.linphone.ui.assistant.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.AssistantLandingFragmentBinding
import org.linphone.ui.assistant.model.AcceptConditionsAndPolicyDialogModel
import org.linphone.ui.assistant.viewmodel.LandingViewModel
import org.linphone.ui.sso.OpenIdActivity
import org.linphone.utils.DialogUtils

@UiThread
class LandingFragment : Fragment() {
    companion object {
        private const val TAG = "[Landing Fragment]"
    }

    private lateinit var binding: AssistantLandingFragmentBinding

    private val viewModel: LandingViewModel by navGraphViewModels(
        R.id.assistant_nav_graph
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AssistantLandingFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        binding.setBackClickListener {
            requireActivity().finish()
        }

        binding.setRegisterClickListener {
            if (viewModel.conditionsAndPrivacyPolicyAccepted) {
                goToRegisterFragment()
            } else {
                showAcceptConditionsAndPrivacyDialog(goToAccountCreate = true)
            }
        }

        binding.setQrCodeClickListener {
            val action = LandingFragmentDirections.actionLandingFragmentToQrCodeScannerFragment()
            findNavController().navigate(action)
        }

        binding.setThirdPartySipAccountLoginClickListener {
            if (viewModel.conditionsAndPrivacyPolicyAccepted) {
                goToLoginThirdPartySipAccountFragment()
            } else {
                showAcceptConditionsAndPrivacyDialog(goToThirdPartySipAccountLogin = true)
            }
        }

        viewModel.redirectToDigestAuthEvent.observe(viewLifecycleOwner) {
            it.consume {
                goToLoginFragment()
            }
        }

        viewModel.redirectToSingleSignOnEvent.observe(viewLifecycleOwner) {
            it.consume {
                goToSingleSignOnActivity()
            }
        }
    }

    private fun goToLoginFragment() {
        val identity = viewModel.sipIdentity.value.orEmpty()
        val action = LandingFragmentDirections.actionLandingFragmentToLoginFragment(identity)
        findNavController().navigate(action)
    }

    private fun goToSingleSignOnActivity() {
        startActivity(Intent(requireContext(), OpenIdActivity::class.java))
        requireActivity().finish()
    }

    private fun goToRegisterFragment() {
        val action = LandingFragmentDirections.actionLandingFragmentToRegisterFragment()
        findNavController().navigate(action)
    }

    private fun goToLoginThirdPartySipAccountFragment() {
        val action = LandingFragmentDirections.actionLandingFragmentToThirdPartySipAccountWarningFragment()
        findNavController().navigate(action)
    }

    private fun showAcceptConditionsAndPrivacyDialog(
        goToAccountCreate: Boolean = false,
        goToThirdPartySipAccountLogin: Boolean = false
    ) {
        val model = AcceptConditionsAndPolicyDialogModel()
        val dialog = DialogUtils.getAcceptConditionsAndPrivacyDialog(
            requireActivity(),
            model
        )

        model.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                dialog.dismiss()
            }
        }

        model.conditionsAcceptedEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Conditions & Privacy policy have been accepted")
                coreContext.postOnCoreThread {
                    corePreferences.conditionsAndPrivacyPolicyAccepted = true
                }
                dialog.dismiss()

                if (goToAccountCreate) {
                    goToRegisterFragment()
                } else if (goToThirdPartySipAccountLogin) {
                    goToLoginThirdPartySipAccountFragment()
                }
            }
        }

        model.privacyPolicyClickedEvent.observe(viewLifecycleOwner) {
            it.consume {
                val url = getString(R.string.website_privacy_policy_url)
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(browserIntent)
                } catch (ise: IllegalStateException) {
                    Log.e(
                        "$TAG Can't start ACTION_VIEW intent for URL [$url], IllegalStateException: $ise"
                    )
                }
            }
        }

        model.generalTermsClickedEvent.observe(viewLifecycleOwner) {
            it.consume {
                val url = getString(R.string.website_terms_and_conditions_url)
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(browserIntent)
                } catch (ise: IllegalStateException) {
                    Log.e(
                        "$TAG Can't start ACTION_VIEW intent for URL [$url], IllegalStateException: $ise"
                    )
                }
            }
        }

        dialog.show()
    }
}
