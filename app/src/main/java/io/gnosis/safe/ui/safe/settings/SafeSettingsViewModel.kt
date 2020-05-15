package io.gnosis.safe.ui.safe.settings

import io.gnosis.data.models.Safe
import io.gnosis.data.repositories.SafeRepository
import io.gnosis.safe.Tracker
import io.gnosis.safe.ui.base.AppDispatchers
import io.gnosis.safe.ui.base.BaseStateViewModel
import kotlinx.coroutines.flow.collect
import javax.inject.Inject

class SafeSettingsViewModel @Inject constructor(
    private val safeRepository: SafeRepository,
    private val tracker: Tracker,
    appDispatchers: AppDispatchers
) : BaseStateViewModel<SafeSettingsState>(appDispatchers) {

    override fun initialState() = SafeSettingsState.SafeLoading(null)

    init {
        safeLaunch {
            safeRepository.activeSafeFlow().collect { safe ->
                updateState { SafeSettingsState.SafeSettings(safe, null) }
            }
        }
    }

    fun removeSafe() {
        safeLaunch {
            runCatching {
                val safe = safeRepository.getActiveSafe()
                safe?.let {
                    safeRepository.removeSafe(safe)
                }
            }.onFailure {
                updateState { SafeSettingsState.SafeRemoved(ViewAction.ShowError(it)) }
            }.onSuccess {
                updateState {
                    SafeSettingsState.SafeRemoved(
                        ViewAction.NavigateTo(
                            SafeSettingsFragmentDirections.actionSafeSettingsFragmentToNoSafeFragment()
                        )
                    )
                }
                tracker.setNumSafes(safeRepository.getSafes().count())
            }
        }
    }
}

sealed class SafeSettingsState : BaseStateViewModel.State {

    data class SafeLoading(
        override var viewAction: BaseStateViewModel.ViewAction?
    ) : SafeSettingsState()

    data class SafeSettings(
        val safe: Safe?,
        override var viewAction: BaseStateViewModel.ViewAction?
    ) : SafeSettingsState()

    data class SafeRemoved(
        override var viewAction: BaseStateViewModel.ViewAction?
    ) : SafeSettingsState()
}
