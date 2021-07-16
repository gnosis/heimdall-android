package io.gnosis.safe.di.modules

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.net.ConnectivityManager
import android.os.Build
import androidx.work.WorkManager
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import io.gnosis.data.BuildConfig.CLIENT_GATEWAY_URL
import io.gnosis.data.adapters.dataMoshi
import io.gnosis.data.backend.GatewayApi
import io.gnosis.data.db.daos.OwnerDao
import io.gnosis.data.repositories.*
import io.gnosis.data.security.BiometricPasscodeManager
import io.gnosis.data.security.HeimdallEncryptionManager
import io.gnosis.safe.BuildConfig
import io.gnosis.safe.R
import io.gnosis.safe.Tracker
import io.gnosis.safe.di.ApplicationContext
import io.gnosis.safe.helpers.ConnectivityInfoProvider
import io.gnosis.safe.notifications.NotificationManager
import io.gnosis.safe.notifications.NotificationRepository
import io.gnosis.safe.notifications.NotificationServiceApi
import io.gnosis.safe.ui.base.AppDispatchers
import io.gnosis.safe.ui.settings.chain.paging.ChainPagingProvider
import io.gnosis.safe.ui.terms.TermsChecker
import io.gnosis.safe.ui.transactions.paging.TransactionPagingProvider
import io.gnosis.safe.utils.BalanceFormatter
import io.gnosis.safe.utils.MnemonicKeyAndAddressDerivator
import io.gnosis.safe.utils.ParamSerializer
import io.gnosis.safe.workers.HeimdallWorkerFactory
import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import pm.gnosis.ethereum.rpc.EthereumRpcConnector
import pm.gnosis.ethereum.rpc.retrofit.RetrofitEthereumRpcApi
import pm.gnosis.ethereum.rpc.retrofit.RetrofitEthereumRpcConnector
import pm.gnosis.mnemonic.Bip39
import pm.gnosis.mnemonic.Bip39Generator
import pm.gnosis.mnemonic.android.AndroidWordListProvider
import pm.gnosis.mnemonic.wordlists.WordListProvider
import pm.gnosis.svalinn.common.PreferencesManager
import pm.gnosis.svalinn.common.utils.QrCodeGenerator
import pm.gnosis.svalinn.common.utils.ZxingQrCodeGenerator
import pm.gnosis.svalinn.security.KeyStorage
import pm.gnosis.svalinn.security.impls.AesEncryptionManager
import pm.gnosis.svalinn.security.impls.AndroidKeyStorage
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
class ApplicationModule(private val application: Application) {
    companion object {
        const val INFURA_REST_CLIENT = "infuraRestClient"
    }

    @Provides
    @Singleton
    @ApplicationContext
    fun providesContext(): Context = application

    @Provides
    @Singleton
    fun providesApplication(): Application = application

    @Provides
    @Singleton
    fun providesAppDispatchers(): AppDispatchers = AppDispatchers()

    @Provides
    @Singleton
    fun providesConnectivityManager(@ApplicationContext context: Context): ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Provides
    @Singleton
    fun providesPreferencesManager(@ApplicationContext context: Context): PreferencesManager = PreferencesManager(context)

    @Provides
    @Singleton
    fun providesTermsChecker(preferencesManager: PreferencesManager): TermsChecker = TermsChecker(preferencesManager)

    @Provides
    @Singleton
    fun providesTracker(@ApplicationContext context: Context): Tracker = Tracker.getInstance(context)

    @Provides
    @Singleton
    fun providesEthereumRpcConnector(retrofitEthereumRpcApi: RetrofitEthereumRpcApi): EthereumRpcConnector =
        RetrofitEthereumRpcConnector(retrofitEthereumRpcApi, BuildConfig.BLOCKCHAIN_NET_URL)

    @Provides
    @Singleton
    fun providesMoshi(): Moshi = dataMoshi

    @Provides
    @Singleton
    fun providesEthereumJsonRpcApi(moshi: Moshi, @Named(INFURA_REST_CLIENT) client: OkHttpClient): RetrofitEthereumRpcApi =
        Retrofit.Builder()
            .client(client)
            .baseUrl(BuildConfig.BLOCKCHAIN_NET_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RetrofitEthereumRpcApi::class.java)

    @Provides
    @Singleton
    fun providesGatewayApi(moshi: Moshi, client: OkHttpClient): GatewayApi =
        Retrofit.Builder()
            .client(client)
            .baseUrl(GatewayApi.BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GatewayApi::class.java)

    @Provides
    @Singleton
    fun providesNotificationServiceApi(moshi: Moshi, client: OkHttpClient): NotificationServiceApi =
        Retrofit.Builder()
            .client(client)
            .baseUrl(NotificationServiceApi.BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(NotificationServiceApi::class.java)

    @Provides
    @Singleton
    @Named(INFURA_REST_CLIENT)
    fun providesInfuraOkHttpClient(
        okHttpClient: OkHttpClient,
        @Named(InterceptorsModule.REST_CLIENT_INTERCEPTORS) interceptors: @JvmSuppressWildcards List<Interceptor>
    ): OkHttpClient =
        okHttpClient.newBuilder().apply {
            interceptors.forEach {
                addInterceptor(it)
            }
        }.build()

    @Provides
    @Singleton
    fun providesOkHttpClient(
        @Named(InterceptorsModule.REST_CLIENT_INTERCEPTORS) interceptors: @JvmSuppressWildcards List<Interceptor>
    ): OkHttpClient =
        OkHttpClient.Builder().apply {
            addInterceptor(interceptors[1])
            addInterceptor(interceptors[2])
            connectTimeout(30, TimeUnit.SECONDS)
            readTimeout(30, TimeUnit.SECONDS)
            writeTimeout(30, TimeUnit.SECONDS)
            pingInterval(5, TimeUnit.SECONDS)
            certificatePinner(buildCertificatePinner())
        }.build()

    private fun buildCertificatePinner(): CertificatePinner {
        val pins = BuildConfig.SSL_PINS as Map<String, String>
        return CertificatePinner.Builder().apply {
            pins.keys.forEach { domainPattern ->
                pins[domainPattern]?.split(",")?.forEach { hash ->
                    add(domainPattern, hash)
                }
            }
        }.build()
    }

    @Provides
    @Singleton
    fun providesChainPagingProvider(chainInfoRepository: ChainInfoRepository): ChainPagingProvider =
        ChainPagingProvider(chainInfoRepository)

    @Provides
    @Singleton
    fun providesTransactionPagingProvider(transactionRepository: TransactionRepository): TransactionPagingProvider =
        TransactionPagingProvider(transactionRepository)

    @Provides
    @Singleton
    fun providesQrCodeGenerator(): QrCodeGenerator = ZxingQrCodeGenerator()

    @Provides
    @Singleton
    fun providesNotificationRepo(
        safeRepository: SafeRepository,
        credentialsRepository: CredentialsRepository,
        preferencesManager: PreferencesManager,
        notificationServiceApi: NotificationServiceApi,
        notificationManager: NotificationManager
    ): NotificationRepository =
        NotificationRepository(safeRepository, credentialsRepository, preferencesManager, notificationServiceApi, notificationManager)

    @Provides
    @Singleton
    fun providesNotificationManager(
        @ApplicationContext context: Context,
        preferencesManager: PreferencesManager,
        balanceFormatter: BalanceFormatter,
        safeRepository: SafeRepository
    ): NotificationManager = NotificationManager(context, preferencesManager, balanceFormatter, safeRepository)

    @Provides
    @Singleton
    fun providesConnectivityInfoProvider(connectivityManager: ConnectivityManager): ConnectivityInfoProvider =
        ConnectivityInfoProvider(connectivityManager)

    @Provides
    fun providesBalanceFormatter(): BalanceFormatter = BalanceFormatter()

    @Provides
    @Singleton
    fun providesParamSerializer(moshi: Moshi): ParamSerializer = ParamSerializer(moshi)

    @Provides
    @Singleton
    fun providesBip39(wordListProvider: WordListProvider): Bip39 = Bip39Generator(wordListProvider)

    @Provides
    fun providesWordListProvider(@ApplicationContext context: Context): WordListProvider = AndroidWordListProvider(context)

    @Provides
    @Singleton
    fun providesMnemonicKeyAndAddressDerivator(bip39: Bip39): MnemonicKeyAndAddressDerivator = MnemonicKeyAndAddressDerivator(bip39)

    @Provides
    @Singleton
    fun providesEncryptionManager(
        preferencesManager: PreferencesManager,
        keyStorage: KeyStorage,
        @ApplicationContext context: Context
    ): HeimdallEncryptionManager =
        // We use 4k iterations to keep the memory used during password setup below 16mb (theoretical minimum vm heap for Android 4.4)
        HeimdallEncryptionManager(preferencesManager, keyStorage, 4096, context)

    @Provides
    @Singleton
    fun providesBiometricPasscodeManager(
        preferencesManager: PreferencesManager,
        keyStorage: KeyStorage,
        @ApplicationContext context: Context
    ): BiometricPasscodeManager =
        // We use 4k iterations to keep the memory used during password setup below 16mb (theoretical minimum vm heap for Android 4.4)
        HeimdallEncryptionManager(preferencesManager, keyStorage, 4096, context)

    //TODO: remove
    @Provides
    @Singleton
    fun providesAesEncryptionManager(
        preferencesManager: PreferencesManager,
        keyStorage: KeyStorage
    ): AesEncryptionManager =
        // We use 4k iterations to keep the memory used during password setup below 16mb (theoretical minimum vm heap for Android 4.4)
        AesEncryptionManager(application, preferencesManager, keyStorage, 4096)

    @Provides
    @Singleton
    fun providesKeyStorage(@ApplicationContext context: Context): KeyStorage =
        // FIXME This is a workaround for a problem in Android Marshmallow See: https://issuetracker.google.com/issues/37095309
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            val localeCopy = Locale.getDefault()
            setLocale(context, Locale.ENGLISH)
            val androidKeyStorage = AndroidKeyStorage(context)
            setLocale(context, localeCopy)
            androidKeyStorage
        } else {
            AndroidKeyStorage(context)
        }

    private fun setLocale(context: Context, locale: Locale) {
        Locale.setDefault(locale)
        val resources: Resources = context.getResources()
        val config: Configuration = resources.getConfiguration()
        config.locale = locale
        resources.updateConfiguration(config, resources.getDisplayMetrics())
    }

    //TODO: remove
    @Provides
    @Singleton
    fun providesOwnerVault(
        encryptionManager: AesEncryptionManager,
        preferencesManager: PreferencesManager
    ): OwnerCredentialsRepository = OwnerCredentialsVault(encryptionManager, preferencesManager)

    @Provides
    @Singleton
    fun providesCredentialsRepository(
        ownerDao: OwnerDao,
        encryptionManager: HeimdallEncryptionManager,
        ownerVault: OwnerCredentialsRepository
    ): CredentialsRepository =
        CredentialsRepository(ownerDao, encryptionManager, ownerVault)

    @Provides
    @Singleton
    fun providesFirebaseRemoteConfig(): FirebaseRemoteConfig {
        val remoteConfig = Firebase.remoteConfig
        val configSettings = remoteConfigSettings {
            // increase the number of fetches available per hour during development.
            minimumFetchIntervalInSeconds = if (CLIENT_GATEWAY_URL.contains("staging")) 0 else 43200 // 12 hours
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        // Set default Remote Config parameter values. An app uses the in-app default values, and
        // when you need to adjust those defaults, you set an updated value for only the values you
        // want to change in the Firebase console.
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)
        return remoteConfig
    }

    @Provides
    @Singleton
    fun providesWorkerManager(
        @ApplicationContext context: Context,
        safeRepository: SafeRepository,
        chainInfoRepository: ChainInfoRepository,
        notificationRepository: NotificationRepository,
        tracker: Tracker
    ): WorkManager {
        WorkManager.initialize(
            context,
            androidx.work.Configuration.Builder()
                .setWorkerFactory(HeimdallWorkerFactory(safeRepository, chainInfoRepository, notificationRepository, tracker))
                .build()
        )
        return WorkManager.getInstance(context)
    }
}
