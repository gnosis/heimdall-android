package io.gnosis.safe.ui.splash

import android.app.Application
import android.content.Context
import io.gnosis.data.models.Owner
import io.gnosis.data.repositories.CredentialsRepository
import io.gnosis.data.repositories.SafeRepository
import io.gnosis.safe.TestLifecycleRule
import io.gnosis.safe.Tracker
import io.gnosis.safe.appDispatchers
import io.gnosis.safe.notifications.NotificationRepository
import io.gnosis.safe.test
import io.gnosis.safe.ui.base.BaseStateViewModel
import io.gnosis.safe.ui.terms.TermsChecker
import io.gnosis.safe.workers.WorkRepository
import io.mockk.*
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import pm.gnosis.svalinn.common.PreferencesManager
import pm.gnosis.tests.utils.TestPreferences

class SplashViewModelTest {

    private val tracker: Tracker = mockk()
    private val notificationRepository = mockk<NotificationRepository>()
    private val safeRepository = mockk<SafeRepository>()
    private val ownerCredentialsRepository = mockk<CredentialsRepository>()
    private val workRepository = mockk<WorkRepository>()
    private lateinit var preferences: TestPreferences
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var termsChecker: TermsChecker
    private lateinit var context: Context

    @get:Rule
    val instantExecutorRule = TestLifecycleRule()

    @Before
    fun setup() {
        preferences = spyk()
        val application = mockk<Application>().apply {
            every { getSharedPreferences(any(), any()) } returns preferences
        }
        preferencesManager = PreferencesManager(application)
        context = mockk(relaxed = true)
        termsChecker = mockk(relaxed = true)
    }

    @Test
    fun `onStartClicked (terms already agreed) should emit StartActivity`() {
        coEvery { termsChecker.getTermsAgreed() } returns true
        val viewModel =
            SplashViewModel(notificationRepository, safeRepository, tracker, termsChecker, ownerCredentialsRepository, workRepository, appDispatchers, context)

        viewModel.onStartClicked()

        with(viewModel.state.test().values()) {
            assertEquals(1, size)
            assertTrue(this[0].viewAction is BaseStateViewModel.ViewAction.StartActivity)
        }
        coVerify(exactly = 1) { termsChecker.getTermsAgreed() }
    }

    @Test
    fun `onStartClicked (terms not agreed previously) should emit ShowTerms`() {
        coEvery { termsChecker.getTermsAgreed() } returns false
        val viewModel =
            SplashViewModel(notificationRepository, safeRepository, tracker, termsChecker, ownerCredentialsRepository, workRepository, appDispatchers, context)

        viewModel.onStartClicked()

        viewModel.state.test().assertValues(
            SplashViewModel.TermsAgreed(SplashViewModel.ShowTerms)
        )
        coVerify(exactly = 1) { termsChecker.getTermsAgreed() }
    }

    @Test
    fun `handleAgreeClicked (user clicks agree) should emit StartActivity`() {
        val viewModel =
            SplashViewModel(notificationRepository, safeRepository, tracker, termsChecker, ownerCredentialsRepository, workRepository, appDispatchers, context)

        viewModel.handleAgreeClicked()

        with(viewModel.state.test().values()) {
            assertEquals(1, size)
            assertTrue(this[0].viewAction is BaseStateViewModel.ViewAction.StartActivity)
        }
        coVerify(exactly = 1) { termsChecker.setTermsAgreed(true) }
    }

    @Test
    fun `skipSplashScreen (terms not agreed previously) should make get started button visible`() {
        coEvery { termsChecker.getTermsAgreed() } returns false
        val viewModel =
            SplashViewModel(notificationRepository, safeRepository, tracker, termsChecker, ownerCredentialsRepository, workRepository, appDispatchers, context)

        viewModel.skipGetStartedButtonWhenTermsAgreed()

        with(viewModel.state.test().values()) {
            assertEquals(1, size)
            assertTrue(this[0].viewAction is SplashViewModel.ShowButton)
        }
        coVerify(exactly = 1) { termsChecker.getTermsAgreed() }
    }

    @Test
    fun `skipSplashScreen (terms agreed previously) should emit StartActivity`() {
        coEvery { termsChecker.getTermsAgreed() } returns true
        val viewModel =
            SplashViewModel(notificationRepository, safeRepository, tracker, termsChecker, ownerCredentialsRepository, workRepository, appDispatchers, context)

        viewModel.skipGetStartedButtonWhenTermsAgreed()

        with(viewModel.state.test().values()) {
            assertEquals(1, size)
            assertTrue(this[0].viewAction is BaseStateViewModel.ViewAction.StartActivity)
        }
        coVerify(exactly = 1) { termsChecker.getTermsAgreed() }
    }

    @Test
    fun `onAppStart should register notification service and set user properties`() = runBlockingTest {

        coEvery { workRepository.registerForPushNotifications() } just Runs
        coEvery { workRepository.updateChainInfo() } just Runs
        coEvery { notificationRepository.clearNotifications() } just Runs
        coEvery { notificationRepository.checkPermissions() } returns true
        coEvery { tracker.setPushInfo(any()) } just Runs
        coEvery { tracker.setNumSafes(any()) } just Runs
        coEvery { tracker.setNumKeysImported(any()) } just Runs
        coEvery { tracker.setNumKeysGenerated(any()) } just Runs
        coEvery { safeRepository.getSafeCount() } returns 0
        coEvery { ownerCredentialsRepository.ownerCount(Owner.Type.IMPORTED) } returns 1
        coEvery { ownerCredentialsRepository.ownerCount(Owner.Type.GENERATED) } returns 0

        val viewModel =
            SplashViewModel(notificationRepository, safeRepository, tracker, termsChecker, ownerCredentialsRepository, workRepository, appDispatchers, context)

        viewModel.onAppStart()

        coVerifySequence {
            workRepository.registerForPushNotifications()
            workRepository.updateChainInfo()
            notificationRepository.clearNotifications()
            notificationRepository.checkPermissions()
            tracker.setPushInfo(true)
            tracker.setNumSafes(0)
            ownerCredentialsRepository.ownerCount(Owner.Type.IMPORTED)
            tracker.setNumKeysImported(1)
            ownerCredentialsRepository.ownerCount(Owner.Type.GENERATED)
            tracker.setNumKeysGenerated(0)
        }
    }

    @Test
    fun `onAppStart (no credentials in repository) should track 0 num_keys_imported`() = runBlockingTest {

        coEvery { workRepository.registerForPushNotifications() } just Runs
        coEvery { workRepository.updateChainInfo() } just Runs
        coEvery { notificationRepository.register() } just Runs
        coEvery { notificationRepository.clearNotifications() } just Runs
        coEvery { notificationRepository.checkPermissions() } returns true
        coEvery { tracker.setPushInfo(any()) } just Runs
        coEvery { tracker.setNumSafes(any()) } just Runs
        coEvery { tracker.setNumKeysImported(any()) } just Runs
        coEvery { tracker.setNumKeysGenerated(any()) } just Runs
        coEvery { safeRepository.getSafeCount() } returns 0
        coEvery { ownerCredentialsRepository.ownerCount(any()) } returns 0

        val viewModel =
            SplashViewModel(notificationRepository, safeRepository, tracker, termsChecker, ownerCredentialsRepository, workRepository, appDispatchers, context)

        viewModel.onAppStart()

        coVerifySequence {
            workRepository.registerForPushNotifications()
            workRepository.updateChainInfo()
            notificationRepository.clearNotifications()
            notificationRepository.checkPermissions()
            tracker.setPushInfo(true)
            tracker.setNumSafes(0)
            ownerCredentialsRepository.ownerCount(Owner.Type.IMPORTED)
            tracker.setNumKeysImported(0)
            ownerCredentialsRepository.ownerCount(Owner.Type.GENERATED)
            tracker.setNumKeysGenerated(0)
        }
    }
}
