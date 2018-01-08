package pm.gnosis.heimdall.ui.transactions

import android.content.Context
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.observers.TestObserver
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.common.utils.DataResult
import pm.gnosis.heimdall.common.utils.ErrorResult
import pm.gnosis.heimdall.common.utils.QrCodeGenerator
import pm.gnosis.heimdall.common.utils.Result
import pm.gnosis.heimdall.data.repositories.TransactionDetailsRepository
import pm.gnosis.heimdall.data.repositories.TransactionRepository
import pm.gnosis.heimdall.data.repositories.TransactionType
import pm.gnosis.heimdall.data.repositories.models.GasEstimate
import pm.gnosis.heimdall.helpers.SignatureStore
import pm.gnosis.heimdall.ui.exceptions.SimpleLocalizedException
import pm.gnosis.heimdall.ui.transactions.ViewTransactionContract.Info
import pm.gnosis.models.Transaction
import pm.gnosis.models.Wei
import pm.gnosis.tests.utils.ImmediateSchedulersRule
import pm.gnosis.tests.utils.MockUtils
import pm.gnosis.tests.utils.mockGetString
import java.math.BigInteger


@RunWith(MockitoJUnitRunner::class)
class ViewTransactionViewModelTest {
    @JvmField
    @Rule
    val rule = ImmediateSchedulersRule()

    @Mock
    lateinit var contextMock: Context

    @Mock
    lateinit var qrCodeGenerator: QrCodeGenerator

    @Mock
    lateinit var transactionRepositoryMock: TransactionRepository

    @Mock
    lateinit var transactionDetailsRepositoryMock: TransactionDetailsRepository

    private lateinit var viewModel: ViewTransactionViewModel

    @Before
    fun setUp() {
        contextMock.mockGetString()
        viewModel = ViewTransactionViewModel(contextMock, qrCodeGenerator, SignatureStore(contextMock), transactionRepositoryMock, transactionDetailsRepositoryMock)
    }

    private fun testExecuteInfo(info: TransactionRepository.ExecuteInformation, estimateResult: Result<GasEstimate>,
                                expectingEstimate: Boolean, vararg expectedResults: Result<Info>) {
        given(transactionRepositoryMock.loadExecuteInformation(TEST_SAFE, TEST_TRANSACTION)).willReturn(Single.just(info))

        val estimateReturn = when (estimateResult) {
            is DataResult -> Single.just(estimateResult.data)
            is ErrorResult -> Single.error(estimateResult.error)
        }
        if (expectingEstimate) {
            given(transactionRepositoryMock.estimateFees(TEST_SAFE, TEST_TRANSACTION, emptyMap())).willReturn(estimateReturn)
        }

        val testObserver = TestObserver<Result<Info>>()
        viewModel.loadExecuteInfo(TEST_SAFE, TEST_TRANSACTION).subscribe(testObserver)

        testObserver.assertNoErrors()
                .assertValues(*expectedResults)
                .assertComplete()

        then(transactionRepositoryMock).should().loadExecuteInformation(TEST_SAFE, TEST_TRANSACTION)
        if (expectingEstimate) {
            then(transactionRepositoryMock).should().estimateFees(TEST_SAFE, TEST_TRANSACTION, emptyMap())
        }
        then(transactionRepositoryMock).shouldHaveNoMoreInteractions()
        BDDMockito.reset(transactionRepositoryMock)
    }

    private fun buildInfo(transactionStatus: TransactionRepository.ExecuteInformation, estimate: GasEstimate? = null) =
            Info(TEST_SAFE, transactionStatus, emptyMap(), estimate)

    @Test
    fun loadExecuteInfo() {

        // Test execute
        val execute = TransactionRepository.ExecuteInformation(TEST_TRANSACTION_HASH, TEST_TRANSACTION, true, 1, TEST_OWNERS)
        testExecuteInfo(execute, DataResult(TEST_TRANSACTION_FEES), true,
                DataResult(buildInfo(execute)), DataResult(buildInfo(execute, TEST_TRANSACTION_FEES)))

        // Test not owner
        val notOwner = TransactionRepository.ExecuteInformation(TEST_TRANSACTION_HASH, TEST_TRANSACTION, false, 1, TEST_OWNERS)
        testExecuteInfo(notOwner, DataResult(TEST_TRANSACTION_FEES),
                false, DataResult(buildInfo(notOwner)), ErrorResult(SimpleLocalizedException(R.string.error_not_enough_confirmations.toString())))

        // Test not owner with multiple confirms
        val notOwnerMultipleConfirms = TransactionRepository.ExecuteInformation(TEST_TRANSACTION_HASH, TEST_TRANSACTION, false, 2, TEST_OWNERS)
        testExecuteInfo(notOwnerMultipleConfirms, DataResult(TEST_TRANSACTION_FEES),
                false, DataResult(buildInfo(notOwnerMultipleConfirms)), ErrorResult(SimpleLocalizedException(R.string.error_not_enough_confirmations.toString())))

        // Test error loading estimate
        val estimateError = TransactionRepository.ExecuteInformation(TEST_TRANSACTION_HASH, TEST_TRANSACTION, true, 1, TEST_OWNERS)
        val error = IllegalStateException()
        testExecuteInfo(estimateError, ErrorResult(error),
                true, DataResult(buildInfo(estimateError)), ErrorResult(error))
    }

    @Test
    fun loadExecuteInfoError() {
        val error = IllegalStateException()
        given(transactionRepositoryMock.loadExecuteInformation(TEST_SAFE, TEST_TRANSACTION)).willReturn(Single.error(error))

        val testObserver = TestObserver<Result<Info>>()
        viewModel.loadExecuteInfo(TEST_SAFE, TEST_TRANSACTION).subscribe(testObserver)

        testObserver.assertNoErrors()
                .assertValue(ErrorResult(error))
                .assertComplete()
    }

    private fun testSubmitTransactionWithGas(info: TransactionRepository.ExecuteInformation, submitError: Throwable?,
                                             expectingSubmit: Boolean, gasOverride: Wei?,
                                             vararg expectedResults: Result<BigInteger>) {

        given(transactionRepositoryMock.loadExecuteInformation(TEST_SAFE, TEST_TRANSACTION)).willReturn(Single.just(info))

        if (expectingSubmit) {
            val submitReturn = submitError?.let { Completable.error(it) } ?: Completable.complete()
            given(transactionRepositoryMock.submit(TEST_SAFE, TEST_TRANSACTION, emptyMap(), gasOverride)).willReturn(submitReturn)
        }

        val testObserverDefaultGas = TestObserver<Result<BigInteger>>()
        viewModel.submitTransaction(TEST_SAFE, TEST_TRANSACTION, gasOverride).subscribe(testObserverDefaultGas)

        testObserverDefaultGas.assertNoErrors()
                .assertValues(*expectedResults)
                .assertComplete()

        then(transactionRepositoryMock).should().loadExecuteInformation(TEST_SAFE, TEST_TRANSACTION)
        if (expectingSubmit) {
            then(transactionRepositoryMock).should().submit(TEST_SAFE, TEST_TRANSACTION, emptyMap(), gasOverride)
        }
        then(transactionRepositoryMock).shouldHaveNoMoreInteractions()
        BDDMockito.reset(transactionRepositoryMock)
    }

    private fun testSubmitTransaction(info: TransactionRepository.ExecuteInformation, submitError: Throwable?,
                                      expectingSubmit: Boolean, vararg expectedResults: Result<BigInteger>) {
        testSubmitTransactionWithGas(info, submitError, expectingSubmit, null, *expectedResults)
        testSubmitTransactionWithGas(info, submitError, expectingSubmit, TEST_GAS_OVERRIDE, *expectedResults)
    }

    @Test
    fun submitTransaction() {

        // Test execute
        val execute = TransactionRepository.ExecuteInformation(TEST_TRANSACTION_HASH, TEST_TRANSACTION, true, 1, TEST_OWNERS)
        testSubmitTransaction(execute, null, true, DataResult(TEST_SAFE))

        // Test not owner
        val notOwner = TransactionRepository.ExecuteInformation(TEST_TRANSACTION_HASH, TEST_TRANSACTION, false, 1, TEST_OWNERS)
        testSubmitTransaction(notOwner, null,
                false, ErrorResult(SimpleLocalizedException(R.string.error_not_enough_confirmations.toString())))

        // Test not owner with multiple confirms
        val notOwnerMultipleConfirms = TransactionRepository.ExecuteInformation(TEST_TRANSACTION_HASH, TEST_TRANSACTION, false, 2, TEST_OWNERS)
        testSubmitTransaction(notOwnerMultipleConfirms, null,
                false, ErrorResult(SimpleLocalizedException(R.string.error_not_enough_confirmations.toString())))

        // Test error submitting transaction
        val estimateError = TransactionRepository.ExecuteInformation(TEST_TRANSACTION_HASH, TEST_TRANSACTION, true, 1, TEST_OWNERS)
        val error = IllegalStateException()
        testSubmitTransaction(estimateError, error, true, ErrorResult(error))
    }

    @Test
    fun submitTransactionLoadInfoError() {
        val error = IllegalStateException()
        given(transactionRepositoryMock.loadExecuteInformation(TEST_SAFE, TEST_TRANSACTION)).willReturn(Single.error(error))

        val testObserver = TestObserver<Result<BigInteger>>()
        viewModel.submitTransaction(TEST_SAFE, TEST_TRANSACTION, null).subscribe(testObserver)

        testObserver.assertNoErrors()
                .assertValue(ErrorResult(error))
                .assertComplete()
    }

    @Test
    fun checkTransactionType() {
        given(transactionDetailsRepositoryMock.loadTransactionType(MockUtils.any()))
                .willReturn(Single.just(TransactionType.GENERIC))

        val testObserver = TestObserver<TransactionType>()
        val transaction = Transaction(BigInteger.ZERO)
        viewModel.checkTransactionType(transaction).subscribe(testObserver)
        testObserver.assertNoErrors().assertValue(TransactionType.GENERIC).assertComplete()
        then(transactionDetailsRepositoryMock).should().loadTransactionType(transaction)
        then(transactionDetailsRepositoryMock).shouldHaveNoMoreInteractions()
    }

    @Test
    fun checkTransactionTypeError() {
        val error = IllegalStateException()
        given(transactionDetailsRepositoryMock.loadTransactionType(MockUtils.any()))
                .willReturn(Single.error(error))

        val testObserver = TestObserver<TransactionType>()
        val transaction = Transaction(BigInteger.ZERO)
        viewModel.checkTransactionType(transaction).subscribe(testObserver)
        testObserver.assertError(error).assertNoValues().assertTerminated()
        then(transactionDetailsRepositoryMock).should().loadTransactionType(transaction)
        then(transactionDetailsRepositoryMock).shouldHaveNoMoreInteractions()
    }

    companion object {
        private val TEST_SAFE = BigInteger.ZERO
        private val TEST_TRANSACTION_HASH = "SomeHash"
        private val TEST_TRANSACTION = Transaction(BigInteger.ZERO, nonce = BigInteger.TEN)
        private val TEST_OWNERS = listOf(BigInteger.valueOf(13))
        private val TEST_TRANSACTION_FEES = GasEstimate(BigInteger.valueOf(1337), Wei(BigInteger.valueOf(23)))
        private val TEST_GAS_OVERRIDE = Wei(BigInteger.valueOf(7331))
    }

}
