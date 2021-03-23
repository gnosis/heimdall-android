package io.gnosis.safe.ui.settings.app.passcode

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.navigation.fragment.findNavController
import io.gnosis.data.security.HeimdallEncryptionManager
import io.gnosis.safe.R
import io.gnosis.safe.ScreenId
import io.gnosis.safe.databinding.FragmentSettingsCreatePasscodeBinding
import io.gnosis.safe.di.components.ViewComponent
import io.gnosis.safe.ui.base.SafeOverviewBaseFragment.Companion.PASSCODE_SET_RESULT
import io.gnosis.safe.ui.base.fragment.BaseViewBindingFragment
import io.gnosis.safe.ui.settings.app.SettingsHandler
import pm.gnosis.svalinn.common.utils.showKeyboardForView
import timber.log.Timber
import javax.inject.Inject

class CreatePasscodeFragment : BaseViewBindingFragment<FragmentSettingsCreatePasscodeBinding>() {

    override fun screenId() = ScreenId.CREATE_PASSCODE

    @Inject
    lateinit var encryptionManager: HeimdallEncryptionManager

    @Inject
    lateinit var settingsHandler: SettingsHandler

    companion object {
        fun newInstance() = CreatePasscodeFragment()
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentSettingsCreatePasscodeBinding =
        FragmentSettingsCreatePasscodeBinding.inflate(inflater, container, false)

    override fun inject(component: ViewComponent) {
        component.inject(this)
    }

    override fun onResume() {
        super.onResume()
        binding.input.showKeyboardForView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {

            Timber.i("---> createPasscode(create_a_6_digit_passcode)")
            createPasscode.setText(R.string.settings_create_passcode_create_a_6_digit_passcode)
            backButton.setOnClickListener {
                findNavController().popBackStack(R.id.createPasscodeFragment, true)
            }

            val digits = listOf(digit1, digit2, digit3, digit4, digit5, digit6)
            input.showKeyboardForView()
            Timber.i("---> setOnEditorActionListener()")

            input.setOnEditorActionListener { v, actionId, event ->
                actionId == EditorInfo.IME_ACTION_DONE
            }

            input.doOnTextChanged { text, _, _, _ ->
                Timber.i("---> doOnTextChanged: text:  $text, length: ${text?.length ?: 0}")

                text?.let {
                    if (input.text.length < 6) {
                        digits.forEach {
                            it.background = ContextCompat.getDrawable(requireContext(), R.color.surface_01)
                        }
                        (1..text.length).forEach { i ->
                            digits[i - 1].background = ContextCompat.getDrawable(requireContext(), R.drawable.ic_circle_passcode_filled_20dp)
                        }
                    } else {
                        input.setText("") // So it is empty, when the user navigates back
                        findNavController().navigate(
                            CreatePasscodeFragmentDirections.actionCreatePasscodeFragmentToRepeatPasscodeFragment(passcode = text.toString())
                        )
                    }
                }
            }

            skipButton.setOnClickListener {

                Timber.i("---> Skip flow.")
                val popped = findNavController().popBackStack(R.id.createPasscodeFragment, true)
                if (popped) findNavController().popBackStack(R.id.createPasscodeFragment, true)
                settingsHandler.usePasscode = false
                findNavController().currentBackStackEntry?.savedStateHandle?.set(PASSCODE_SET_RESULT, false)
            }
        }
    }
}