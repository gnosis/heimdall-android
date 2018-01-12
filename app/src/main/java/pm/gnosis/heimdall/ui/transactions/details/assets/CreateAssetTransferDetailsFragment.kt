package pm.gnosis.heimdall.ui.transactions.details.assets

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.gojuno.koptional.None
import com.gojuno.koptional.Optional
import com.gojuno.koptional.toOptional
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.widget.itemSelections
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Function3
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.layout_transaction_details_asset_transfer.*
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.common.di.components.ApplicationComponent
import pm.gnosis.heimdall.common.di.components.DaggerViewComponent
import pm.gnosis.heimdall.common.di.modules.ViewModule
import pm.gnosis.heimdall.common.utils.*
import pm.gnosis.heimdall.data.repositories.models.ERC20TokenWithBalance
import pm.gnosis.heimdall.data.repositories.models.Safe
import pm.gnosis.heimdall.ui.base.SimpleSpinnerAdapter
import pm.gnosis.heimdall.ui.transactions.details.base.BaseEditableTransactionDetailsFragment
import pm.gnosis.heimdall.ui.transactions.exceptions.TransactionInputException
import pm.gnosis.models.Transaction
import pm.gnosis.models.TransactionParcelable
import pm.gnosis.utils.asEthereumAddressStringOrNull
import pm.gnosis.utils.hexAsBigIntegerOrNull
import pm.gnosis.utils.nullOnThrow
import pm.gnosis.utils.stringWithNoTrailingZeroes
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject


class CreateAssetTransferDetailsFragment : BaseEditableTransactionDetailsFragment() {

    @Inject
    lateinit var subViewModel: AssetTransferDetailsContract

    private val adapter by lazy {
        // Adapter should only be created if we need it
        TokensSpinnerAdapter(context!!)
    }
    private val safeSubject = BehaviorSubject.createDefault<Optional<BigInteger>>(None)
    private val inputSubject = PublishSubject.create<AssetTransferDetailsContract.InputEvent>()
    private var cachedTransaction: Transaction? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cachedTransaction = arguments?.getParcelable<TransactionParcelable>(ARG_TRANSACTION)?.transaction
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
            = inflater.inflate(R.layout.layout_transaction_details_asset_transfer, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        layout_transaction_details_asset_transfer_max_amount_button.visibility = View.VISIBLE
        layout_transaction_details_asset_transfer_divider_amount.visibility = View.VISIBLE
    }

    override fun onStart() {
        super.onStart()

        val safe = arguments?.getString(ARG_SAFE)?.hexAsBigIntegerOrNull()
        setupSafeSpinner(layout_transaction_details_asset_transfer_safe_input, safe)
        layout_transaction_details_asset_transfer_token_input.adapter = adapter

        disposables += subViewModel.loadFormData(cachedTransaction, true)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(::setupForm, Timber::e)
    }

    private fun setupForm(info: AssetTransferDetailsContract.FormData) {
        layout_transaction_details_asset_transfer_to_input.setText(info.to?.asEthereumAddressStringOrNull())
        layout_transaction_details_asset_transfer_amount_input.setText(info.tokenAmount?.let { info.token?.convertAmount(it)?.stringWithNoTrailingZeroes() })
        layout_transaction_details_asset_transfer_amount_label.text = info.token?.symbol ?: getString(R.string.value)
        disposables += layout_transaction_details_asset_transfer_max_amount_button.clicks()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    nullOnThrow { adapter.getItem(layout_transaction_details_asset_transfer_token_input.selectedItemPosition) }?.let {
                        if (it.balance != null) {
                            val amount = it.token.convertAmount(it.balance).stringWithNoTrailingZeroes()
                            layout_transaction_details_asset_transfer_amount_input.setText(amount)
                        } else {
                            snackbar(view!!, R.string.unknown_balance)
                        }
                    }
                }, Timber::e)
        disposables += observeSafe().flatMap {
            subViewModel.observeTokens(info.selectedToken, it.toNullable())
        }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(::setSpinnerData, { setSpinnerData(AssetTransferDetailsContract.State(0, emptyList())) })

        disposables += Observable.combineLatest(
                prepareInput(layout_transaction_details_asset_transfer_to_input),
                prepareInput(layout_transaction_details_asset_transfer_amount_input),
                layout_transaction_details_asset_transfer_token_input.itemSelections(),
                Function3 { to: CharSequence, amount: CharSequence, tokenIndex: Int ->
                    val token = if (tokenIndex < 0) null else adapter.getItem(tokenIndex)
                    AssetTransferDetailsContract.InputEvent(to.toString() to false, amount.toString() to false, token to false)
                }
        ).subscribe(inputSubject::onNext, Timber::e)
    }

    private fun setSpinnerData(state: AssetTransferDetailsContract.State) {
        this.layout_transaction_details_asset_transfer_token_input?.let {
            adapter.clear()
            adapter.addAll(state.tokens)
            adapter.notifyDataSetChanged()
            it.setSelection(state.selectedIndex)
        }
    }

    override fun observeTransaction(): Observable<Result<Transaction>> {
        return inputSubject
                .compose(subViewModel.inputTransformer(cachedTransaction))
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNextForResult({
                    cachedTransaction = it.copy(nonce = cachedTransaction?.nonce ?: it.nonce)
                }, {
                    (it as? TransactionInputException)?.let {
                        if (it.errorFields and TransactionInputException.TO_FIELD != 0) {
                            setInputError(layout_transaction_details_asset_transfer_to_input)
                        }
                        if (it.errorFields and TransactionInputException.AMOUNT_FIELD != 0) {
                            setInputError(layout_transaction_details_asset_transfer_amount_input)
                        }
                    }
                })
    }

    override fun selectedSafeChanged(safe: Safe?) {
        safeSubject.onNext(safe?.address.toOptional())
    }

    override fun observeSafe(): Observable<Optional<BigInteger>> = safeSubject

    override fun inputEnabled(enabled: Boolean) {
        layout_transaction_details_asset_transfer_safe_input.isEnabled = enabled
        layout_transaction_details_asset_transfer_to_input.isEnabled = enabled
        layout_transaction_details_asset_transfer_amount_input.isEnabled = enabled
        layout_transaction_details_asset_transfer_token_input.isEnabled = enabled
        layout_transaction_details_asset_transfer_max_amount_button.isEnabled = enabled
    }

    override fun inject(component: ApplicationComponent) {
        DaggerViewComponent.builder()
                .applicationComponent(component)
                .viewModule(ViewModule(activity!!))
                .build().inject(this)
    }

    private class TokensSpinnerAdapter(context: Context) : SimpleSpinnerAdapter<ERC20TokenWithBalance>(context) {
        override fun title(item: ERC20TokenWithBalance) =
                item.token.symbol

        override fun subTitle(item: ERC20TokenWithBalance) =
                item.balance?.let { item.token.convertAmount(it).stringWithNoTrailingZeroes() } ?: "-"
    }

    companion object {

        private const val ARG_TRANSACTION = "argument.parcelable.transaction"
        private const val ARG_SAFE = "argument.string.safe"

        fun createInstance(transaction: Transaction?, safeAddress: String?) =
                CreateAssetTransferDetailsFragment().apply {
                    arguments = Bundle().apply {
                        putParcelable(ARG_TRANSACTION, transaction?.parcelable())
                        putString(ARG_SAFE, safeAddress)
                    }
                }
    }
}