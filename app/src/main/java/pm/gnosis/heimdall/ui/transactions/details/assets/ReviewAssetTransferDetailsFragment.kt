package pm.gnosis.heimdall.ui.transactions.details.assets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.gojuno.koptional.Optional
import com.gojuno.koptional.toOptional
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.layout_review_asset_transfer.*
import kotlinx.android.synthetic.main.layout_transaction_details_asset_transfer.*
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.common.di.components.ApplicationComponent
import pm.gnosis.heimdall.common.di.components.DaggerViewComponent
import pm.gnosis.heimdall.common.di.modules.ViewModule
import pm.gnosis.heimdall.common.utils.DataResult
import pm.gnosis.heimdall.common.utils.ErrorResult
import pm.gnosis.heimdall.common.utils.Result
import pm.gnosis.heimdall.ui.transactions.details.base.BaseReviewTransactionDetailsFragment
import pm.gnosis.models.Transaction
import pm.gnosis.models.TransactionParcelable
import pm.gnosis.utils.asEthereumAddressStringOrNull
import pm.gnosis.utils.hexAsBigIntegerOrNull
import pm.gnosis.utils.stringWithNoTrailingZeroes
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject


class ReviewAssetTransferDetailsFragment : BaseReviewTransactionDetailsFragment() {

    @Inject
    lateinit var subViewModel: AssetTransferDetailsContract

    var transaction: Transaction? = null
    var safe: BigInteger? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        safe = arguments?.getString(ARG_SAFE)?.hexAsBigIntegerOrNull()
        transaction = arguments?.getParcelable<TransactionParcelable>(ARG_TRANSACTION)?.transaction
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
            = inflater.inflate(R.layout.layout_review_asset_transfer, container, false)

    override fun onStart() {
        super.onStart()

        disposables += subViewModel.loadFormData(transaction, false)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(::setupForm, Timber::e)
    }

    private fun setupForm(info: AssetTransferDetailsContract.FormData) {
        layout_review_asset_transfer_to.text = info.to?.asEthereumAddressStringOrNull()
        val amount = info.tokenAmount?.let { info.token?.convertAmount(it)?.stringWithNoTrailingZeroes() }
        val tokenSymbol = info.token?.symbol ?: getString(R.string.tokens)
        layout_review_asset_transfer_amount.text = "$amount $tokenSymbol"
        layout_review_asset_transfer_from.text = safe?.asEthereumAddressStringOrNull()
    }

    override fun observeTransaction(): Observable<Result<Transaction>> {
        return Observable.just(transaction.toOptional())
                // Check that the transaction we are displaying is legit
                .compose(subViewModel.transactionTransformer())
    }

    override fun observeSafe(): Observable<Optional<BigInteger>> =
            Observable.just(safe.toOptional())

    override fun inject(component: ApplicationComponent) {
        DaggerViewComponent.builder()
                .applicationComponent(component)
                .viewModule(ViewModule(activity!!))
                .build().inject(this)
    }

    companion object {

        private const val ARG_TRANSACTION = "argument.parcelable.transaction"
        private const val ARG_SAFE = "argument.string.safe"

        fun createInstance(transaction: Transaction?, safeAddress: String?) =
                ReviewAssetTransferDetailsFragment().apply {
                    arguments = Bundle().apply {
                        putParcelable(ARG_TRANSACTION, transaction?.parcelable())
                        putString(ARG_SAFE, safeAddress)
                    }
                }
    }
}