package io.gnosis.safe.di.modules

import androidx.work.WorkManager
import com.unstoppabledomains.config.network.model.Network
import com.unstoppabledomains.resolution.DomainResolution
import com.unstoppabledomains.resolution.Resolution
import com.unstoppabledomains.resolution.naming.service.NamingServiceType
import dagger.Module
import dagger.Provides
import io.gnosis.data.backend.GatewayApi
import io.gnosis.data.db.daos.ChainDao
import io.gnosis.data.db.daos.SafeDao
import io.gnosis.data.repositories.ChainInfoRepository
import io.gnosis.data.repositories.DummyDomainResolution
import io.gnosis.data.repositories.EnsNormalizer
import io.gnosis.data.repositories.EnsRepository
import io.gnosis.data.repositories.IDNEnsNormalizer
import io.gnosis.data.repositories.SafeRepository
import io.gnosis.data.repositories.TokenRepository
import io.gnosis.data.repositories.TransactionRepository
import io.gnosis.data.repositories.UnstoppableDomainsRepository
import io.gnosis.safe.BuildConfig.BLOCKCHAIN_NET_URL
import io.gnosis.safe.BuildConfig.INFURA_API_KEY
import io.gnosis.safe.workers.WorkRepository
import pm.gnosis.ethereum.EthereumRepository
import pm.gnosis.ethereum.rpc.EthereumRpcConnector
import pm.gnosis.ethereum.rpc.RpcEthereumRepository
import pm.gnosis.svalinn.common.PreferencesManager
import timber.log.Timber
import javax.inject.Singleton

@Module
class RepositoryModule {

    @Provides
    @Singleton
    fun provideSafeRepository(
        safeDao: SafeDao,
        preferencesManager: PreferencesManager,
        gatewayApi: GatewayApi
    ): SafeRepository {
        return SafeRepository(safeDao, preferencesManager, gatewayApi)
    }

    @Provides
    @Singleton
    fun provideChainRepository(
        chainDao: ChainDao,
        gatewayApi: GatewayApi
    ): ChainInfoRepository {
        return ChainInfoRepository(chainDao, gatewayApi)
    }

    @Provides
    @Singleton
    fun providesEthereumRepository(ethereumRpcConnector: EthereumRpcConnector): EthereumRepository =
        RpcEthereumRepository(ethereumRpcConnector)

    @Provides
    @Singleton
    fun providesEnsNormalizer(): EnsNormalizer = IDNEnsNormalizer()

    @Provides
    @Singleton
    fun providesEnsRepository(
        ethereumRepository: EthereumRepository,
        ensNormalizer: EnsNormalizer
    ): EnsRepository =
        EnsRepository(ensNormalizer, ethereumRepository)

    @Provides
    @Singleton
    fun providesDomainResolutionLibrary(): DomainResolution {
        val firstWord = BLOCKCHAIN_NET_URL.removePrefix("https://").split(".").first()
        val network = Network.valueOf(firstWord.toUpperCase())
        return try {
            Resolution.builder()
                .chainId(NamingServiceType.CNS, network)
                .infura(NamingServiceType.CNS, INFURA_API_KEY)
                .build()
        } catch (throwable: Throwable) {
            Timber.e(throwable, "Error initializing UnstoppableDomains")
            DummyDomainResolution()
        }
    }

    @Provides
    @Singleton
    fun providesUnstoppableRepository(
        domainResolutionLibrary: DomainResolution
    ): UnstoppableDomainsRepository =
        UnstoppableDomainsRepository(domainResolutionLibrary)

    @Provides
    @Singleton
    fun providesTokenRepository(gatewayApi: GatewayApi): TokenRepository =
        TokenRepository(gatewayApi)

    @Provides
    @Singleton
    fun providesTransactionRepository(gatewayApi: GatewayApi): TransactionRepository =
        TransactionRepository(gatewayApi)

    @Provides
    @Singleton
    fun providesWorkRepository(workManager: WorkManager): WorkRepository =
        WorkRepository(workManager)
}
