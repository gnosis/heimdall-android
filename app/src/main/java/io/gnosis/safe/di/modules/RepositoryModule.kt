package io.gnosis.safe.di.modules

import dagger.Module
import dagger.Provides
import io.gnosis.data.db.daos.SafeDao
import io.gnosis.data.repositories.EnsNormalizer
import io.gnosis.data.repositories.EnsRepository
import io.gnosis.data.repositories.IDNEnsNormalizer
import io.gnosis.data.repositories.SafeRepository
import pm.gnosis.ethereum.EthereumRepository
import pm.gnosis.ethereum.rpc.EthereumRpcConnector
import pm.gnosis.ethereum.rpc.RpcEthereumRepository
import pm.gnosis.svalinn.common.PreferencesManager
import javax.inject.Singleton

@Module
class RepositoryModule {

    @Provides
    @Singleton
    fun provideSafeRepository(
        safeDao: SafeDao,
        preferencesManager: PreferencesManager,
        ethereumRepository: EthereumRepository
    ): SafeRepository {
        return SafeRepository(safeDao, preferencesManager, ethereumRepository)
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
}
