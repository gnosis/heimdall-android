package io.gnosis.safe.ui.transactions

import android.view.View
import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import io.gnosis.data.models.Safe
import io.gnosis.data.models.SafeInfo
import io.gnosis.data.models.Transaction
import io.gnosis.data.models.Transaction.*
import io.gnosis.data.models.TransactionStatus
import io.gnosis.data.repositories.SafeRepository
import io.gnosis.data.repositories.SafeRepository.Companion.METHOD_CHANGE_MASTER_COPY
import io.gnosis.data.repositories.SafeRepository.Companion.METHOD_DISABLE_MODULE
import io.gnosis.data.repositories.SafeRepository.Companion.METHOD_ENABLE_MODULE
import io.gnosis.data.repositories.SafeRepository.Companion.METHOD_SET_FALLBACK_HANDLER
import io.gnosis.data.repositories.SafeRepository.Companion.SAFE_MASTER_COPY_0_0_2
import io.gnosis.data.repositories.SafeRepository.Companion.SAFE_MASTER_COPY_0_1_0
import io.gnosis.data.repositories.SafeRepository.Companion.SAFE_MASTER_COPY_1_0_0
import io.gnosis.data.repositories.SafeRepository.Companion.SAFE_MASTER_COPY_1_1_1
import io.gnosis.data.repositories.TokenRepository.Companion.ETH_SERVICE_TOKEN_INFO
import io.gnosis.data.repositories.getValueByName
import io.gnosis.safe.R
import io.gnosis.safe.ui.base.AppDispatchers
import io.gnosis.safe.ui.base.BaseStateViewModel
import io.gnosis.safe.ui.transactions.paging.TransactionPagingProvider
import io.gnosis.safe.utils.shiftedString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import pm.gnosis.model.Solidity
import pm.gnosis.utils.asEthereumAddress
import java.math.BigInteger
import javax.inject.Inject

class TransactionListViewModel
@Inject constructor(
    private val transactionsPager: TransactionPagingProvider,
    private val safeRepository: SafeRepository,
    appDispatchers: AppDispatchers
) : BaseStateViewModel<TransactionsViewState>(appDispatchers) {

    private var currentSafeAddress: Solidity.Address? = null
    private var currentSafeTxItems: Flow<PagingData<TransactionView>>? = null

    init {
        safeLaunch {
            safeRepository.activeSafeFlow().collect { load() }
        }
    }

    override fun initialState(): TransactionsViewState = TransactionsViewState(null, true)

    fun load(forceLoad: Boolean = false) {
        safeLaunch {
            val safe = safeRepository.getActiveSafe()
            updateState { TransactionsViewState(isLoading = true, viewAction = ActiveSafeChanged(safe)) }
            if (safe != null) {
                val safeInfo = safeRepository.getSafeInfo(safe.address)
                getTransactions(safe.address, safeInfo, forceLoad).collectLatest {
                    updateState {
                        TransactionsViewState(
                            isLoading = false,
                            viewAction = LoadTransactions(it)
                        )
                    }
                }
            } else {
                updateState(forceViewAction = true) { TransactionsViewState(isLoading = false, viewAction = NoSafeSelected) }
            }
        }
    }

    fun getTransactions(safe: Solidity.Address, safeInfo: SafeInfo, forceLoad: Boolean): Flow<PagingData<TransactionView>> {

        val lastResult = currentSafeTxItems
        if (!forceLoad && safe == currentSafeAddress && lastResult != null) {
            return lastResult
        }

        currentSafeAddress = safe

        val safeTxItems: Flow<PagingData<TransactionView>> = transactionsPager.getTransactionsStream(safe, safeInfo)
            .map { pagingData ->
                pagingData
                    .map { transaction -> getTransactionView(transaction, safeInfo) }
                    .filter { it !is TransactionView.Unknown }
            }
            .map {
                // insert headers
                it.insertSeparators { before, after ->

                    if (after == null) {
                        // we're at the end of the list
                        return@insertSeparators null  // no separator
                    }

                    if (before == null) {
                        // we're at the beginning of the list

                       return@insertSeparators if (after.isQueued()) {
                            TransactionView.SectionHeader(title = R.string.tx_list_queue)
                        } else if (after.isHistory()) {
                            TransactionView.SectionHeader(title = R.string.tx_list_history)
                        } else {
                            null // no separator
                        }
                    }

                    // we're in the middle of the list
                    if (before.isQueued() && after.isHistory()) {
                        // insert history separator after queued transaction
                        TransactionView.SectionHeader(title = R.string.tx_list_history)
                    } else {
                        null // no separator
                    }
                }
            }
            .cachedIn(viewModelScope)

        currentSafeTxItems = safeTxItems
        return safeTxItems
    }

    fun getTransactionView(transaction: Transaction, safeInfo: SafeInfo): TransactionView {
        return when {
            transaction is Transfer && isHistoricTransfer(transaction) -> historicTransfer(transaction, safeInfo)
            transaction is Transfer && isQueuedTransfer(transaction) -> queuedTransfer(transaction, safeInfo)
            transaction is SettingsChange && isQueuedMastercopyChange(transaction) -> queuedMastercopyChange(
                transaction,
                safeInfo.threshold
            )
            transaction is SettingsChange && isHistoricMastercopyChange(transaction) -> historicMastercopyChange(transaction)
            transaction is SettingsChange && isQueuedSetFallbackHandler(transaction) -> queuedSetFallbackHandler(
                transaction,
                safeInfo.threshold
            )
            transaction is SettingsChange && isHistoricSetFallbackHandler(transaction) -> historicSetFallbackHandler(transaction)
            transaction is SettingsChange && isQueuedModuleChange(transaction) -> queuedModuleChange(transaction, safeInfo.threshold)
            transaction is SettingsChange && isHistoricModuleChange(transaction) -> historicModuleChange(transaction)
            transaction is SettingsChange && isQueuedSettingsChange(transaction) -> queuedSettingsChange(transaction, safeInfo.threshold)
            transaction is SettingsChange && isHistoricSettingsChange(transaction) -> historicSettingsChange(transaction)
            transaction is Custom && isQueuedCustomTransaction(transaction) -> queuedCustomTransaction(transaction, safeInfo)
            transaction is Custom && isHistoricCustomTransaction(transaction) -> historicCustomTransaction(transaction, safeInfo)
            else -> TransactionView.Unknown
        } as TransactionView
    }

    private fun isHistoricSetFallbackHandler(settingsChange: SettingsChange): Boolean {
        return METHOD_SET_FALLBACK_HANDLER == settingsChange.dataDecoded.method && isCompleted(settingsChange.status)
    }

    private fun isQueuedSetFallbackHandler(settingsChange: SettingsChange): Boolean {
        return METHOD_SET_FALLBACK_HANDLER == settingsChange.dataDecoded.method && !isCompleted(settingsChange.status)
    }

    private fun isHistoricModuleChange(settingsChange: SettingsChange): Boolean {
        return (METHOD_ENABLE_MODULE == settingsChange.dataDecoded.method || METHOD_DISABLE_MODULE == settingsChange.dataDecoded.method) && isCompleted(
            settingsChange.status
        )
    }

    private fun isQueuedModuleChange(settingsChange: SettingsChange): Boolean {
        return (METHOD_ENABLE_MODULE == settingsChange.dataDecoded.method || METHOD_DISABLE_MODULE == settingsChange.dataDecoded.method) && !isCompleted(
            settingsChange.status
        )
    }

    private fun isHistoricMastercopyChange(settingsChange: SettingsChange): Boolean {
        return METHOD_CHANGE_MASTER_COPY == settingsChange.dataDecoded.method && isCompleted(settingsChange.status)
    }

    private fun isQueuedMastercopyChange(settingsChange: SettingsChange): Boolean {
        return METHOD_CHANGE_MASTER_COPY == settingsChange.dataDecoded.method && !isCompleted(settingsChange.status)
    }

    private fun isHistoricCustomTransaction(custom: Custom): Boolean {
        return isCompleted(custom.status)
    }

    private fun isQueuedCustomTransaction(custom: Custom): Boolean {
        return !isCompleted(custom.status)
    }

    private fun isHistoricSettingsChange(settingsChange: SettingsChange): Boolean {
        return METHOD_CHANGE_MASTER_COPY != settingsChange.dataDecoded.method && isCompleted(settingsChange.status)
    }

    private fun isQueuedSettingsChange(settingsChange: SettingsChange): Boolean {
        return METHOD_CHANGE_MASTER_COPY != settingsChange.dataDecoded.method && !isCompleted(settingsChange.status)
    }

    private fun isHistoricTransfer(transfer: Transfer): Boolean {
        return isCompleted(transfer.status)
    }

    private fun isQueuedTransfer(transfer: Transfer): Boolean {
        return !isCompleted(transfer.status)
    }

    private fun isCompleted(status: TransactionStatus): Boolean =
        when (status) {
            TransactionStatus.AwaitingConfirmations,
            TransactionStatus.AwaitingExecution,
            TransactionStatus.Pending -> false
            TransactionStatus.Success,
            TransactionStatus.Failed,
            TransactionStatus.Cancelled -> true
        }

    private fun historicTransfer(
        transfer: Transfer,
        safeInfo: SafeInfo
    ): TransactionView.Transfer {
        val isIncoming: Boolean = transfer.recipient == safeInfo.address

        return TransactionView.Transfer(
            status = transfer.status,
            statusText = displayString(transfer.status),
            statusColorRes = statusTextColor(transfer.status),
            amountText = formatTransferAmount(transfer, isIncoming),
            dateTimeText = transfer.date ?: "",
            txTypeIcon = if (isIncoming) R.drawable.ic_arrow_green_16dp else R.drawable.ic_arrow_red_10dp,
            address = if (isIncoming) transfer.sender else transfer.recipient,
            amountColor = if (transfer.value > BigInteger.ZERO && isIncoming) R.color.safe_green else R.color.gnosis_dark_blue,
            alpha = alpha(transfer)
        )
    }

    private fun queuedTransfer(transfer: Transfer, safeInfo: SafeInfo): TransactionView? {
        val thresholdMet = checkThreshold(safeInfo.threshold, transfer.confirmations)
        val isIncoming: Boolean = transfer.recipient == safeInfo.address

        return TransactionView.TransferQueued(
            status = transfer.status,
            statusText = displayString(transfer.status),
            statusColorRes = statusTextColor(transfer.status),
            amountText = formatTransferAmount(transfer, isIncoming),
            dateTimeText = transfer.date ?: "",
            txTypeIcon = if (isIncoming) R.drawable.ic_arrow_green_16dp else R.drawable.ic_arrow_red_10dp,
            address = if (isIncoming) transfer.sender else transfer.recipient,
            amountColor = if (transfer.value > BigInteger.ZERO && isIncoming) R.color.safe_green else R.color.gnosis_dark_blue,
            confirmations = transfer.confirmations ?: 0,
            threshold = safeInfo.threshold,
            confirmationsTextColor = if (thresholdMet) R.color.safe_green else R.color.medium_grey,
            confirmationsIcon = if (thresholdMet) R.drawable.ic_confirmations_green_16dp else R.drawable.ic_confirmations_grey_16dp,
            nonce = transfer.nonce.toString()
        )
    }

    private fun historicSettingsChange(transaction: SettingsChange): TransactionView.SettingsChange =
        TransactionView.SettingsChange(
            status = transaction.status,
            statusText = displayString(transaction.status),
            statusColorRes = statusTextColor(transaction.status),
            dateTimeText = transaction.date ?: "",
            method = transaction.dataDecoded.method,
            alpha = alpha(transaction)
        )

    private fun queuedSettingsChange(transaction: SettingsChange, threshold: Int): TransactionView.SettingsChangeQueued {
        val thresholdMet = checkThreshold(threshold, transaction.confirmations)

        return TransactionView.SettingsChangeQueued(
            status = transaction.status,
            statusText = displayString(transaction.status),
            statusColorRes = statusTextColor(transaction.status),
            dateTimeText = transaction.date ?: "",
            settingNameText = transaction.dataDecoded.method,
            confirmations = transaction.confirmations ?: 0,
            threshold = threshold,
            confirmationsTextColor = if (thresholdMet) R.color.safe_green else R.color.medium_grey,
            confirmationsIcon = if (thresholdMet) R.drawable.ic_confirmations_green_16dp else R.drawable.ic_confirmations_grey_16dp,
            nonce = transaction.nonce.toString()
        )
    }

    private fun historicMastercopyChange(transaction: SettingsChange): TransactionView.SettingsChangeVariant {

        val address = getAddress(transaction, "_masterCopy")
        val version = getVersionForAddress(address)

        return TransactionView.SettingsChangeVariant(
            status = transaction.status,
            statusText = displayString(transaction.status),
            statusColorRes = statusTextColor(transaction.status),
            dateTimeText = transaction.date ?: "",
            alpha = alpha(transaction),
            version = version,
            address = address,
            label = R.string.tx_list_change_mastercopy
        )
    }

    private fun queuedSetFallbackHandler(transaction: SettingsChange, threshold: Int): TransactionView.SettingsChangeVariantQueued {
        val thresholdMet = checkThreshold(threshold, transaction.confirmations)
        val address = getAddress(transaction, "handler")
        val version = getVersionForAddress(address)

        return TransactionView.SettingsChangeVariantQueued(
            status = transaction.status,
            statusText = displayString(transaction.status),
            statusColorRes = statusTextColor(transaction.status),
            dateTimeText = transaction.date ?: "",
            version = "DefaultFallbackHandler",
            address = address,
            label = R.string.tx_list_set_fallback_handler,
            confirmations = transaction.confirmations ?: 0,
            threshold = threshold,
            confirmationsTextColor = if (thresholdMet) R.color.safe_green else R.color.medium_grey,
            confirmationsIcon = if (thresholdMet) R.drawable.ic_confirmations_green_16dp else R.drawable.ic_confirmations_grey_16dp,
            nonce = transaction.nonce.toString()
        )
    }

    @StringRes
    private fun displayString(status: TransactionStatus): Int =
        when (status) {
            TransactionStatus.AwaitingConfirmations -> R.string.tx_list_awaiting_confirmations
            TransactionStatus.AwaitingExecution -> R.string.tx_list_awaiting_execution
            TransactionStatus.Cancelled -> R.string.tx_list_cancelled
            TransactionStatus.Failed -> R.string.tx_list_failed
            TransactionStatus.Success -> R.string.tx_list_success
            TransactionStatus.Pending -> R.string.tx_list_pending
        }

    private fun historicSetFallbackHandler(transaction: SettingsChange): TransactionView.SettingsChangeVariant {
        val address = getAddress(transaction, "handler")

        return TransactionView.SettingsChangeVariant(
            status = transaction.status,
            statusText = displayString(transaction.status),
            statusColorRes = statusTextColor(transaction.status),
            dateTimeText = transaction.date ?: "",
            alpha = alpha(transaction),
            version = "DefaultFallbackHandler",
            address = address,
            label = R.string.tx_list_set_fallback_handler
        )
    }

    private fun queuedModuleChange(transaction: SettingsChange, threshold: Int): TransactionView.SettingsChangeVariantQueued {
        val thresholdMet = checkThreshold(threshold, transaction.confirmations)
        val address = getAddress(transaction, "module")
        val label = if (transaction.dataDecoded.method == "enableModule") R.string.tx_list_enable_module else R.string.tx_list_disable_module

        return TransactionView.SettingsChangeVariantQueued(
            status = transaction.status,
            statusText = displayString(transaction.status),
            statusColorRes = statusTextColor(transaction.status),
            dateTimeText = transaction.date ?: "",
            version = "",
            address = address,
            label = label,
            confirmations = transaction.confirmations ?: 0,
            threshold = threshold,
            confirmationsTextColor = if (thresholdMet) R.color.safe_green else R.color.medium_grey,
            confirmationsIcon = if (thresholdMet) R.drawable.ic_confirmations_green_16dp else R.drawable.ic_confirmations_grey_16dp,
            nonce = transaction.nonce.toString(),
            visibilityVersion = View.INVISIBLE,
            visibilityEllipsizedAddress = View.INVISIBLE,
            visibilityModuleAddress = View.VISIBLE
        )
    }

    private fun historicModuleChange(transaction: SettingsChange): TransactionView.SettingsChangeVariant {

        val address = getAddress(transaction, "module")
        val label = if (transaction.dataDecoded.method == "enableModule") R.string.tx_list_enable_module else R.string.tx_list_disable_module

        return TransactionView.SettingsChangeVariant(
            status = transaction.status,
            statusText = displayString(transaction.status),
            statusColorRes = statusTextColor(transaction.status),
            dateTimeText = transaction.date ?: "",
            alpha = alpha(transaction),
            address = address,
            label = label,
            version = "",
            visibilityVersion = View.INVISIBLE,
            visibilityEllipsizedAddress = View.INVISIBLE,
            visibilityModuleAddress = View.VISIBLE
        )
    }

    private fun queuedMastercopyChange(transaction: SettingsChange, threshold: Int): TransactionView.SettingsChangeVariantQueued {
        val thresholdMet = checkThreshold(threshold, transaction.confirmations)
        val address = getAddress(transaction, "_masterCopy")
        val version = getVersionForAddress(address)

        return TransactionView.SettingsChangeVariantQueued(
            status = transaction.status,
            statusText = displayString(transaction.status),
            statusColorRes = statusTextColor(transaction.status),
            dateTimeText = transaction.date ?: "",
            confirmations = transaction.confirmations ?: 0,
            threshold = threshold,
            confirmationsTextColor = if (thresholdMet) R.color.safe_green else R.color.medium_grey,
            confirmationsIcon = if (thresholdMet) R.drawable.ic_confirmations_green_16dp else R.drawable.ic_confirmations_grey_16dp,
            nonce = transaction.nonce.toString(),
            version = version,
            address = address,
            label = R.string.tx_list_change_mastercopy
        )
    }

    private fun getVersionForAddress(address: Solidity.Address?): String =
        when (address) {
            SAFE_MASTER_COPY_0_0_2 -> "0.0.2"
            SAFE_MASTER_COPY_0_1_0 -> "0.1.0"
            SAFE_MASTER_COPY_1_0_0 -> "1.0.0"
            SAFE_MASTER_COPY_1_1_1 -> "1.1.1"
            else -> "unknown"
        }

    private fun getAddress(transaction: SettingsChange, key: String): Solidity.Address? =
        transaction.dataDecoded.parameters.getValueByName(key)?.asEthereumAddress()

    private fun historicCustomTransaction(custom: Custom, safeInfo: SafeInfo): TransactionView.CustomTransaction {
        val isIncoming: Boolean = custom.address == safeInfo.address
        return TransactionView.CustomTransaction(
            status = custom.status,
            statusText = displayString(custom.status),
            statusColorRes = statusTextColor(custom.status),
            dateTimeText = custom.date ?: "",
            address = custom.address,
            dataSizeText = if (custom.dataSize > 0) "${custom.dataSize} bytes" else "",
            amountText = formatAmount(isIncoming, custom.value, ETH_SERVICE_TOKEN_INFO.decimals, ETH_SERVICE_TOKEN_INFO.symbol),
            amountColor = if (custom.value > BigInteger.ZERO && isIncoming) R.color.safe_green else R.color.gnosis_dark_blue,
            alpha = alpha(custom)
        )
    }

    private fun queuedCustomTransaction(custom: Custom, safeInfo: SafeInfo): TransactionView.CustomTransactionQueued {
        val isIncoming: Boolean = custom.address == safeInfo.address
        val thresholdMet = checkThreshold(safeInfo.threshold, custom.confirmations)
        return TransactionView.CustomTransactionQueued(
            status = custom.status,
            statusText = displayString(custom.status),
            statusColorRes = statusTextColor(custom.status),
            dateTimeText = custom.date ?: "",
            address = custom.address,
            confirmations = custom.confirmations ?: 0,
            threshold = safeInfo.threshold,
            confirmationsTextColor = if (thresholdMet) R.color.safe_green else R.color.medium_grey,
            confirmationsIcon = if (thresholdMet) R.drawable.ic_confirmations_green_16dp else R.drawable.ic_confirmations_grey_16dp,
            nonce = custom.nonce.toString(),
            dataSizeText = if (custom.dataSize > 0) "${custom.dataSize} bytes" else "",
            amountText = formatAmount(isIncoming, custom.value, ETH_SERVICE_TOKEN_INFO.decimals, ETH_SERVICE_TOKEN_INFO.symbol),
            amountColor = if (custom.value > BigInteger.ZERO && isIncoming) R.color.safe_green else R.color.gnosis_dark_blue
        )
    }

    private fun formatTransferAmount(viewTransfer: Transfer, incoming: Boolean): String {
        val symbol = viewTransfer.tokenInfo?.symbol
        return formatAmount(incoming, viewTransfer.value, viewTransfer.tokenInfo?.decimals ?: 0, symbol)
    }

    private fun formatAmount(incoming: Boolean, value: BigInteger, decimals: Int, symbol: String?): String {
        val inOut = if (value == BigInteger.ZERO) "" else if (incoming) "+" else "-"
        val decimalValue = value.shiftedString(decimals = decimals)
        return "%s%s %s".format(inOut, decimalValue, symbol)
    }

    private fun statusTextColor(status: TransactionStatus): Int {
        return when (status) {
            TransactionStatus.Success -> R.color.safe_green
            TransactionStatus.Cancelled -> R.color.dark_grey
            TransactionStatus.AwaitingExecution,
            TransactionStatus.AwaitingConfirmations,
            TransactionStatus.Pending -> R.color.safe_pending_orange
            else -> R.color.safe_failed_red
        }
    }

    private fun alpha(transaction: Transaction): Float =
        when (transaction.status) {
            TransactionStatus.Failed, TransactionStatus.Cancelled -> OPACITY_HALF
            else -> OPACITY_FULL
        }

    private fun checkThreshold(safeThreshold: Int, txThreshold: Int?): Boolean {
        return txThreshold?.let {
            it >= safeThreshold
        } ?: false
    }

    companion object {
        const val OPACITY_FULL = 1.0F
        const val OPACITY_HALF = 0.5F
    }
}

data class TransactionsViewState(
    override var viewAction: BaseStateViewModel.ViewAction?,
    val isLoading: Boolean
) : BaseStateViewModel.State

data class LoadTransactions(
    val newTransactions: PagingData<TransactionView>
) : BaseStateViewModel.ViewAction

data class ActiveSafeChanged(
    val activeSafe: Safe?
) : BaseStateViewModel.ViewAction

object NoSafeSelected : BaseStateViewModel.ViewAction

fun TransactionView.isQueued() = when (status) {
    TransactionStatus.Pending,
    TransactionStatus.AwaitingConfirmations,
    TransactionStatus.AwaitingExecution -> true
    else -> false
}

fun TransactionView.isHistory() = when (status) {
    TransactionStatus.Cancelled,
    TransactionStatus.Failed,
    TransactionStatus.Success -> true
    else -> false
}