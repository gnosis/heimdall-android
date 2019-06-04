package pm.gnosis.heimdall.ui.safe.recover.safe.submit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.jakewharton.rxbinding2.view.clicks
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.layout_recovering_safe_fund.*
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.data.repositories.models.RecoveringSafe
import pm.gnosis.heimdall.di.components.ApplicationComponent
import pm.gnosis.heimdall.di.components.DaggerViewComponent
import pm.gnosis.heimdall.di.modules.ViewModule
import pm.gnosis.heimdall.reporting.Event
import pm.gnosis.heimdall.reporting.EventTracker
import pm.gnosis.heimdall.reporting.ScreenId
import pm.gnosis.heimdall.ui.base.BaseFragment
import pm.gnosis.heimdall.ui.dialogs.share.SimpleAddressShareDialog
import pm.gnosis.heimdall.ui.safe.main.SafeMainActivity
import pm.gnosis.heimdall.utils.errorSnackbar
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.shareExternalText
import pm.gnosis.svalinn.common.utils.subscribeForResult
import pm.gnosis.svalinn.common.utils.withArgs
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.asEthereumAddressString
import timber.log.Timber
import java.math.RoundingMode
import javax.inject.Inject

class RecoveringSafeFundFragment : BaseFragment() {

    override fun inject(component: ApplicationComponent) =
        DaggerViewComponent.builder()
            .viewModule(ViewModule(context!!))
            .applicationComponent(component)
            .build().inject(this)

    @Inject
    lateinit var viewModel: RecoveringSafeContract

    @Inject
    lateinit var eventTracker: EventTracker

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.layout_recovering_safe_fund, container, false)

    override fun onStart() {
        super.onStart()
        eventTracker.submit(Event.ScreenView(ScreenId.RECOVER_SAFE_AWAIT_FUNDS))

        layout_recovering_safe_fund_description.text = getString(R.string.fund_recovery_fees, "-", "-")
        val safeAddress = arguments?.getString(EXTRA_SAFE_ADDRESS)?.asEthereumAddress() ?: throw IllegalStateException("No safe address provided!")
        disposables += viewModel.observeRecoveryInfo(safeAddress)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeForResult(onNext = ::onRecoveryInfo, onError = {
                errorSnackbar(layout_recovering_safe_fund_qr_code_button, it)
            })

        disposables += viewModel.checkRecoveryFunded(safeAddress)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(onSuccess = ::onRecoveryFunded, onError = Timber::e)

        disposables += layout_recovering_safe_fund_qr_code_button.clicks()
            .subscribeBy(onNext = {
                SimpleAddressShareDialog.create(layout_recovering_safe_fund_address.text.toString())
                    .show(activity!!.supportFragmentManager, null)
            }, onError = Timber::e)

        disposables += layout_recovering_safe_fund_share_button.clicks()
            .subscribeBy(onNext = {
                context!!.shareExternalText(layout_recovering_safe_fund_address.text.toString())
            }, onError = Timber::e)
    }

    private fun onRecoveryFunded(safeAddress: Solidity.Address) {
        startActivity(SafeMainActivity.createIntent(context!!, safeAddress))
    }

    private fun onRecoveryInfo(info: RecoveringSafeContract.RecoveryInfo) {
        layout_recovering_safe_fund_address.text = info.safeAddress
        info.paymentToken?.let {
            val requiredFundsString = it.token.displayString(info.paymentAmount)
            layout_recovering_safe_fund_amount_label.text = getString(R.string.pending_safe_deposit_value, requiredFundsString)
            layout_recovering_safe_fund_description.text =
                getString(R.string.fund_recovery_fees, it.displayString(roundingMode = RoundingMode.UP), requiredFundsString)
        }
    }

    companion object {
        private const val EXTRA_SAFE_ADDRESS = "extra.string.safe_address"

        fun createInstance(safe: RecoveringSafe) = RecoveringSafeFundFragment().withArgs(Bundle().apply {
            putString(EXTRA_SAFE_ADDRESS, safe.address.asEthereumAddressString())
        })
    }
}
