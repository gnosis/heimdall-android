package pm.gnosis.heimdall.accounts.repositories.impls

import io.reactivex.Completable
import io.reactivex.Single
import okio.ByteString
import org.ethereum.geth.Address
import org.ethereum.geth.BigInt
import org.ethereum.geth.Geth
import org.ethereum.geth.KeyStore
import pm.gnosis.crypto.KeyGenerator
import pm.gnosis.heimdall.accounts.base.exceptions.InvalidTransactionParams
import pm.gnosis.heimdall.accounts.base.models.Account
import pm.gnosis.heimdall.accounts.base.models.Signature
import pm.gnosis.heimdall.accounts.base.repositories.AccountsRepository
import pm.gnosis.heimdall.common.PreferencesManager
import pm.gnosis.heimdall.common.utils.edit
import pm.gnosis.mnemonic.Bip39
import pm.gnosis.models.Transaction
import pm.gnosis.utils.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GethAccountsRepository @Inject constructor(
        private val gethAccountManager: GethAccountManager,
        private val gethKeyStore: KeyStore,
        private val preferencesManager: PreferencesManager,
        private val bip39: Bip39
) : AccountsRepository {

    override fun loadActiveAccount(): Single<Account> {
        return Single.fromCallable {
            gethAccountManager.getActiveAccount()
        }.map {
            Account(it.address.hex.hexAsBigInteger())
        }
    }

    override fun signTransaction(transaction: Transaction): Single<String> {
        return Single.fromCallable {
            val account = gethAccountManager.getActiveAccount()

            if (!transaction.signable()) {
                throw InvalidTransactionParams()
            }

            val tx = Geth.newTransaction(
                    transaction.nonce!!.toLong(),
                    Address(transaction.address.asEthereumAddressString()),
                    BigInt(transaction.value?.toLong() ?: 0),
                    BigInt(transaction.gas!!.toLong()),
                    BigInt(transaction.gasPrice!!.toLong()),
                    transaction.data?.hexStringToByteArray() ?: ByteArray(0))

            val signed = gethKeyStore.signTxPassphrase(
                    account, gethAccountManager.getAccountPassphrase(), tx, BigInt(transaction.chainId.toLong()))

            signed.encodeRLP().toHexString()
        }
    }

    override fun sign(data: ByteArray): Single<Signature> {
        return Single.fromCallable {
            val account = gethAccountManager.getActiveAccount()

            val signature = gethKeyStore.signHashPassphrase(account, gethAccountManager.getAccountPassphrase(), data).toHexString()

            val r = signature.substring(0, 64).hexAsBigInteger()
            val s = signature.substring(64, 128).hexAsBigInteger()
            val v = signature.substring(128, 130).hexAsBigInteger().toByte()
            Signature(r, s, v)
        }
    }

    override fun saveAccountFromMnemonic(mnemonic: String, accountIndex: Long): Completable =
            Single.fromCallable {
                val hdNode = KeyGenerator().masterNode(ByteString.of(*bip39.mnemonicToSeed(mnemonic)))
                hdNode.derive(KeyGenerator.BIP44_PATH_ETHEREUM).deriveChild(accountIndex).keyPair
            }.flatMapCompletable {
                saveAccount(it.privKeyBytes ?: throw IllegalStateException("Private key must not be null"))
            }


    override fun saveMnemonic(mnemonic: String): Completable = Completable.fromCallable {
        preferencesManager.prefs.edit {
            //TODO: in the future this needs to be encrypted
            putString(PreferencesManager.MNEMONIC_KEY, mnemonic)
        }
    }

    override fun saveAccount(privateKey: ByteArray): Completable =
            Completable.fromCallable {
                gethKeyStore.importECDSAKey(privateKey, gethAccountManager.getAccountPassphrase())
            }

    override fun generateMnemonic(): Single<String> = Single.fromCallable { bip39.generateMnemonic() }

    override fun validateMnemonic(mnemonic: String): Single<String> = Single.fromCallable { bip39.validateMnemonic(mnemonic) }
}
