package io.gnosis.safe.utils

import android.content.res.Resources
import io.gnosis.data.BuildConfig
import io.gnosis.data.models.transaction.TransactionDirection
import io.gnosis.data.models.transaction.TransferInfo
import io.gnosis.data.repositories.SafeRepository
import io.gnosis.safe.R
import io.gnosis.safe.ui.transactions.AddressInfoData
import io.gnosis.safe.ui.transactions.details.viewdata.SettingsInfoViewData
import io.gnosis.safe.ui.transactions.details.viewdata.TransactionInfoViewData
import io.gnosis.safe.ui.transactions.details.view.ActionInfoItem
import pm.gnosis.model.Solidity
import java.math.BigInteger

const val DEFAULT_ERC20_SYMBOL = "ERC20"
const val DEFAULT_ERC721_SYMBOL = "NFT"

fun TransactionInfoViewData.formattedAmount(balanceFormatter: BalanceFormatter): String =
    when (val txInfo = this) {
        is TransactionInfoViewData.Custom -> {
            balanceFormatter.formatAmount(txInfo.value, false, 18, BuildConfig.NATIVE_CURRENCY_SYMBOL)
        }
        is TransactionInfoViewData.Transfer -> {
            val incoming = txInfo.direction == TransactionDirection.INCOMING
            val decimals: Int = when (val transferInfo = txInfo.transferInfo) {
                is TransferInfo.Erc20Transfer -> {
                    transferInfo.decimals ?: 0
                }
                is TransferInfo.EtherTransfer -> 18
                else -> 0
            }
            val symbol: String = when (val transferInfo = txInfo.transferInfo) {
                is TransferInfo.Erc20Transfer -> {
                    transferInfo.tokenSymbol ?: DEFAULT_ERC20_SYMBOL
                }
                is TransferInfo.Erc721Transfer -> {
                    transferInfo.tokenSymbol ?: DEFAULT_ERC721_SYMBOL
                }
                else -> {
                    BuildConfig.NATIVE_CURRENCY_SYMBOL
                }
            }
            val value = when (val transferInfo = txInfo.transferInfo) {
                is TransferInfo.Erc20Transfer -> {
                    transferInfo.value
                }
                is TransferInfo.Erc721Transfer -> {
                    BigInteger.ONE
                }

                is TransferInfo.EtherTransfer -> {
                    transferInfo.value
                }

            }
            balanceFormatter.formatAmount(value, incoming, decimals, symbol)
        }
        is TransactionInfoViewData.SettingsChange -> "0 ${BuildConfig.NATIVE_CURRENCY_SYMBOL}"
        is TransactionInfoViewData.Creation -> "0 ${BuildConfig.NATIVE_CURRENCY_SYMBOL}"
        TransactionInfoViewData.Unknown -> "0 ${BuildConfig.NATIVE_CURRENCY_SYMBOL}"
    }

fun TransactionInfoViewData.logoUri(): String? =
    when (val transactionInfo = this) {
        is TransactionInfoViewData.Transfer -> when (val transferInfo = transactionInfo.transferInfo) {
            is TransferInfo.Erc20Transfer -> {
                transferInfo.logoUri
            }
            is TransferInfo.Erc721Transfer -> {
                transferInfo.logoUri
            }
            else -> {
                "local::native_currency"
            }
        }
        is TransactionInfoViewData.Custom, is TransactionInfoViewData.SettingsChange, is TransactionInfoViewData.Creation, TransactionInfoViewData.Unknown -> "local::native_currency"
    }

fun TransactionInfoViewData.SettingsChange.txActionInfoItems(resources: Resources): List<ActionInfoItem> {
    val settingsMethodTitle = mapOf(
        SafeRepository.METHOD_ADD_OWNER_WITH_THRESHOLD to R.string.tx_details_add_owner,
        SafeRepository.METHOD_CHANGE_MASTER_COPY to R.string.tx_details_new_mastercopy,
        SafeRepository.METHOD_CHANGE_THRESHOLD to R.string.tx_details_change_required_confirmations,
        SafeRepository.METHOD_DISABLE_MODULE to R.string.tx_details_disable_module,
        SafeRepository.METHOD_ENABLE_MODULE to R.string.tx_details_enable_module,
        SafeRepository.METHOD_REMOVE_OWNER to R.string.tx_details_remove_owner,
        SafeRepository.METHOD_SET_FALLBACK_HANDLER to R.string.tx_details_set_fallback_handler,
        SafeRepository.METHOD_SWAP_OWNER to R.string.tx_details_remove_owner
    )
    val result = mutableListOf<ActionInfoItem>()
    val settingsChange = this

    when (val settingsInfo = settingsChange.settingsInfo) {
        is SettingsInfoViewData.ChangeImplementation -> {
            val mainCopy = settingsInfo.implementation
            val label = settingsInfo.implementationInfo?.getLabel() ?: resources.getString(mainCopy.implementationVersion())

            result.add(
                getAddressActionInfoItem(
                    settingsInfo.implementation,
                    settingsMethodTitle[SafeRepository.METHOD_CHANGE_MASTER_COPY],
                    settingsInfo.implementationInfo,
                    label
                )
            )
        }
        is SettingsInfoViewData.ChangeThreshold -> {
            val value = settingsInfo.threshold.toString()
            result.add(ActionInfoItem.Value(itemLabel = settingsMethodTitle[SafeRepository.METHOD_CHANGE_THRESHOLD], value = value))
        }
        is SettingsInfoViewData.AddOwner -> {
            result.add(
                getAddressActionInfoItem(
                    settingsInfo.owner,
                    settingsMethodTitle[SafeRepository.METHOD_ADD_OWNER_WITH_THRESHOLD],
                    settingsInfo.ownerInfo
                )
            )
            result.add(ActionInfoItem.Value(settingsMethodTitle[SafeRepository.METHOD_CHANGE_THRESHOLD], settingsInfo.threshold.toString()))
        }
        is SettingsInfoViewData.RemoveOwner -> {
            result.add(getAddressActionInfoItem(settingsInfo.owner, settingsMethodTitle[SafeRepository.METHOD_REMOVE_OWNER], settingsInfo.ownerInfo))
            result.add(ActionInfoItem.Value(settingsMethodTitle[SafeRepository.METHOD_CHANGE_THRESHOLD], settingsInfo.threshold.toString()))
        }
        is SettingsInfoViewData.SetFallbackHandler -> {

            val label: String? = settingsInfo.handlerInfo?.getLabel()
                ?: if (SafeRepository.DEFAULT_FALLBACK_HANDLER == settingsInfo.handler) {
                    resources.getString(R.string.default_fallback_handler)
                } else {
                    resources.getString(R.string.unknown_fallback_handler)
                }
            result.add(
                getAddressActionInfoItem(
                    settingsInfo.handler,
                    settingsMethodTitle[SafeRepository.METHOD_SET_FALLBACK_HANDLER],
                    settingsInfo.handlerInfo,
                    label
                )
            )
        }
        is SettingsInfoViewData.SwapOwner -> {
            result.add(
                getAddressActionInfoItem(
                    settingsInfo.oldOwner,
                    settingsMethodTitle[SafeRepository.METHOD_REMOVE_OWNER],
                    settingsInfo.oldOwnerInfo
                )
            )
            result.add(
                getAddressActionInfoItem(
                    settingsInfo.newOwner,
                    settingsMethodTitle[SafeRepository.METHOD_ADD_OWNER_WITH_THRESHOLD],
                    settingsInfo.newOwnerInfo
                )
            )
        }
        is SettingsInfoViewData.EnableModule -> {
            result.add(
                getAddressActionInfoItem(
                    settingsInfo.module,
                    settingsMethodTitle[SafeRepository.METHOD_ENABLE_MODULE],
                    settingsInfo.moduleInfo
                )
            )
        }
        is SettingsInfoViewData.DisableModule -> {
            result.add(
                getAddressActionInfoItem(
                    settingsInfo.module,
                    settingsMethodTitle[SafeRepository.METHOD_DISABLE_MODULE],
                    settingsInfo.moduleInfo
                )
            )
        }
    }

    return result
}

private fun getAddressActionInfoItem(
    address: Solidity.Address,
    title: Int?,
    ownerInfo: AddressInfoData?,
    label: String? = null
): ActionInfoItem =
    if (ownerInfo?.getLabel() != null || label != null) {
        ActionInfoItem.AddressWithLabel(
            title,
            address,
            label ?: ownerInfo?.getLabel(),
            ownerInfo?.getUrl()
        )
    } else {
        ActionInfoItem.Address(
            title,
            address
        )
    }


fun AddressInfoData.getLabel(): String? =
    when (this) {
        is AddressInfoData.Local -> name
        is AddressInfoData.Remote -> name
        else -> null
    }

fun AddressInfoData.getUrl(): String? =
    when (this) {
        is AddressInfoData.Remote -> addressLogoUri
        else -> null
    }
