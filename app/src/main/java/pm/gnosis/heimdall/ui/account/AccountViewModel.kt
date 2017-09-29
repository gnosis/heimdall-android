package pm.gnosis.heimdall.ui.account

import android.arch.persistence.room.EmptyResultSetException
import android.content.Context
import io.reactivex.Observable
import io.reactivex.functions.Function
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.accounts.base.models.Account
import pm.gnosis.heimdall.accounts.base.repositories.AccountsRepository
import pm.gnosis.heimdall.common.di.ApplicationContext
import pm.gnosis.heimdall.common.util.QrCodeGenerator
import pm.gnosis.heimdall.common.util.mapToResult
import pm.gnosis.heimdall.data.remote.EthereumJsonRpcRepository
import pm.gnosis.heimdall.ui.exceptions.LocalizedException
import javax.inject.Inject

class AccountViewModel @Inject constructor(
        @ApplicationContext private val context: Context,
        private val accountsRepository: AccountsRepository,
        private val ethereumJsonRpcRepository: EthereumJsonRpcRepository,
        private val qrCodeGenerator: QrCodeGenerator
) : AccountContract() {

    override fun getQrCode(contents: String) =
            qrCodeGenerator.generateQrCode(contents)
                    .mapToResult()

    override fun getAccountAddress() =
            accountsRepository.loadActiveAccount()
                    .toObservable()
                    .onErrorResumeNext(Function { throwable ->
                        Observable.error<Account>(if (throwable is EmptyResultSetException)
                            LocalizedException(context.getString(R.string.no_account_available))
                        else
                            throwable)
                    })
                    .mapToResult()

    override fun getAccountBalance() =
            ethereumJsonRpcRepository.getBalance()
                    .mapToResult()
}
