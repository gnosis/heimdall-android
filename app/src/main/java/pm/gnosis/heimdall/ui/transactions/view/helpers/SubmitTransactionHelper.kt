package pm.gnosis.heimdall.ui.transactions.view.helpers

import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import pm.gnosis.heimdall.data.repositories.PushServiceRepository
import pm.gnosis.heimdall.data.repositories.TokenRepository
import pm.gnosis.heimdall.data.repositories.TransactionData
import pm.gnosis.heimdall.data.repositories.TransactionExecutionRepository
import pm.gnosis.heimdall.data.repositories.models.SafeTransaction
import pm.gnosis.heimdall.helpers.AddressHelper
import pm.gnosis.heimdall.helpers.SignatureStore
import pm.gnosis.heimdall.ui.transactions.view.TransactionInfoViewHolder
import pm.gnosis.heimdall.ui.transactions.view.helpers.SubmitTransactionHelper.Events
import pm.gnosis.heimdall.ui.transactions.view.helpers.SubmitTransactionHelper.ViewUpdate
import pm.gnosis.heimdall.ui.transactions.view.viewholders.AssetTransferViewHolder
import pm.gnosis.heimdall.ui.transactions.view.viewholders.GenericTransactionViewHolder
import pm.gnosis.heimdall.utils.emitAndNext
import pm.gnosis.model.Solidity
import pm.gnosis.models.Wei
import pm.gnosis.svalinn.accounts.base.models.Signature
import pm.gnosis.svalinn.common.utils.DataResult
import pm.gnosis.svalinn.common.utils.ErrorResult
import pm.gnosis.svalinn.common.utils.Result
import pm.gnosis.svalinn.common.utils.mapToResult
import pm.gnosis.utils.addHexPrefix
import pm.gnosis.utils.toHexString
import java.math.BigInteger
import javax.inject.Inject

interface SubmitTransactionHelper {

    fun setup(safe: Solidity.Address, executionInfo: (SafeTransaction) -> Single<TransactionExecutionRepository.ExecuteInformation>)

    fun observe(events: Events, transactionData: TransactionData): Observable<Result<ViewUpdate>>

    data class Events(val retry: Observable<Unit>, val requestConfirmations: Observable<Unit>, val submit: Observable<Unit>)

    sealed class ViewUpdate {
        data class TransactionInfo(val viewHolder: TransactionInfoViewHolder) : ViewUpdate()
        data class Estimate(val fees: Wei, val balance: Wei) : ViewUpdate()
        object EstimateError : ViewUpdate()
        data class Confirmations(val isReady: Boolean) : ViewUpdate()
        object ConfirmationsRequested : ViewUpdate()
        object ConfirmationsError : ViewUpdate()
        object TransactionRejected : ViewUpdate()
        data class TransactionSubmitted(val success: Boolean) : ViewUpdate()
    }
}

class DefaultSubmitTransactionHelper @Inject constructor(
    private val addressHelper: AddressHelper,
    private val executionRepository: TransactionExecutionRepository,
    private val signaturePushRepository: PushServiceRepository,
    private val signatureStore: SignatureStore,
    private val tokenRepository: TokenRepository
) : SubmitTransactionHelper {

    private lateinit var safe: Solidity.Address
    private lateinit var executionInfo: (SafeTransaction) -> Single<TransactionExecutionRepository.ExecuteInformation>

    override fun setup(safe: Solidity.Address, executionInfo: (SafeTransaction) -> Single<TransactionExecutionRepository.ExecuteInformation>) {
        this.safe = safe
        this.executionInfo = executionInfo
    }

    override fun observe(events: Events, transactionData: TransactionData): Observable<Result<ViewUpdate>> =
        transactionViewHolder(transactionData)
            .emitAndNext(
                emit = {
                    DataResult(
                        ViewUpdate.TransactionInfo(
                            it
                        )
                    )
                },
                next = { estimation(events, it) })

    private fun estimation(events: Events, viewHolder: TransactionInfoViewHolder) =
        viewHolder.loadTransaction().flatMapObservable { tx ->
            events.retry
                .subscribeOn(AndroidSchedulers.mainThread())
                .startWith(Unit)
                .switchMapSingle { executionInfo(tx).mapToResult() }
        }.emitAndNext(
            emit = {
                it.map {
                    ViewUpdate.Estimate(
                        Wei(
                            (it.txGas + it.dataGas + BigInteger.valueOf(32000)) * it.gasPrice
                        ), it.balance
                    )
                }
            },
            next = { confirmations(events, it) })

    private fun confirmations(events: Events, params: Result<TransactionExecutionRepository.ExecuteInformation>) =
        (params as? DataResult)?.data?.let {
            // Once we have the execution information we can setup everything related to requesting and receiving confirmation
            Observable.merge(
                requestConfirmation(events, it),
                observeConfirmationStore(events, it),
                observeIncomingConfirmations(it)
            )
        } ?: Observable.just<Result<ViewUpdate>>(
            DataResult(ViewUpdate.EstimateError)
        )

    private fun requestConfirmation(events: Events, params: TransactionExecutionRepository.ExecuteInformation) =
        if ((params.requiredConfirmation == 1 && params.isOwner))
            Observable.empty<Result<ViewUpdate>>()
        else
            events.requestConfirmations
                .subscribeOn(AndroidSchedulers.mainThread())
                .startWith(Unit)
                .switchMapSingle {
                    val targets = params.owners - params.sender // TODO: exclude owners that already confirmed
                    executionRepository.calculateHash(safe, params.transaction, params.txGas, params.dataGas, params.gasPrice)
                        .flatMapCompletable {
                            signaturePushRepository.requestConfirmations(
                                it.toHexString().addHexPrefix(),
                                safe,
                                params.transaction,
                                params.txGas,
                                params.dataGas,
                                params.gasPrice,
                                targets.toSet()
                            )

                        }
                        .mapToResult()
                }
                .flatMap {
                    when (it) {
                        is DataResult -> Observable.just(DataResult(ViewUpdate.ConfirmationsRequested))
                        is ErrorResult -> Observable.fromArray(DataResult(ViewUpdate.ConfirmationsError), ErrorResult<ViewUpdate>(it.error))
                    }
                }

    private fun observeConfirmationStore(events: Events, params: TransactionExecutionRepository.ExecuteInformation) =
        signatureStore.flatMapInfo(
            safe, params
        ).publish {
            Observable.merge(
                it.map {
                    val threshold = params.requiredConfirmation - (if (params.isOwner) 1 else 0)
                    DataResult(
                        ViewUpdate.Confirmations(
                            it.size >= threshold
                        )
                    )
                },
                it.switchMap { signatures ->
                    events.submit
                        .subscribeOn(AndroidSchedulers.mainThread())
                        .switchMap { submitTransaction(params, signatures) }
                }
            )
        }

    private fun submitTransaction(params: TransactionExecutionRepository.ExecuteInformation, signatures: Map<Solidity.Address, Signature>) =
        executionRepository.submit(safe, params.transaction, signatures, params.isOwner, params.txGas, params.dataGas, params.gasPrice)
            .andThen(
                Observable.just<ViewUpdate>(
                    ViewUpdate.TransactionSubmitted(
                        true
                    )
                )
            )
            .onErrorResumeNext { t: Throwable ->
                // Propagate error to display snackbar then propagate status
                Observable.just<ViewUpdate>(
                    ViewUpdate.TransactionSubmitted(
                        false
                    )
                ).concatWith(Observable.error(t))
            }
            .mapToResult()

    private fun observeIncomingConfirmations(params: TransactionExecutionRepository.ExecuteInformation) =
        signaturePushRepository.observe(params.transactionHash)
            .flatMap {
                when (it) {
                    is PushServiceRepository.TransactionResponse.Confirmed ->
                        executionRepository.checkConfirmation(safe, params.transaction, params.txGas, params.dataGas, params.gasPrice, it.signature)
                            .map(signatureStore::add)
                            .flatMapObservable { Observable.empty<Result<ViewUpdate>>() }
                            .onErrorResumeNext { e: Throwable -> Observable.just(ErrorResult(e)) }
                    is PushServiceRepository.TransactionResponse.Rejected ->
                        executionRepository.checkRejection(safe, params.transaction, params.txGas, params.dataGas, params.gasPrice, it.signature)
                            .filter { (sender) -> params.owners.contains(sender) }
                            .map<Result<ViewUpdate>> { DataResult(ViewUpdate.TransactionRejected) }
                            .toObservable()
                            .onErrorResumeNext { e: Throwable -> Observable.just(ErrorResult(e)) }
                }
            }

    private fun transactionViewHolder(transactionData: TransactionData): Single<TransactionInfoViewHolder> =
        Single.fromCallable {
            when (transactionData) {
                is TransactionData.Generic ->
                    GenericTransactionViewHolder(safe, transactionData, addressHelper)
                is TransactionData.AssetTransfer ->
                    AssetTransferViewHolder(
                        safe,
                        transactionData,
                        addressHelper,
                        tokenRepository
                    )
            }
        }

}
