package pm.gnosis.heimdall.data.remote

import io.reactivex.Completable
import io.reactivex.Single
import pm.gnosis.heimdall.data.remote.models.*
import retrofit2.http.*


interface RelayServiceApi {
    @POST("v1/safes/{address}/transactions/")
    fun execute(@Path("address") address: String, @Body params: ExecuteParams): Single<RelayExecution>

    @POST("v1/safes/{address}/transactions/estimate/")
    fun estimate(@Path("address") address: String, @Body params: EstimateParams): Single<RelayEstimate>

    @POST("v1/safes/")
    fun safeCreation(@Body params: RelaySafeCreationParams): Single<RelaySafeCreation>

    @POST("v2/safes/")
    fun safeCreation2(@Body params: RelaySafeCreation2Params): Single<RelaySafeCreation2>

    @PUT("v2/safes/{address}/funded/")
    fun notifySafeFunded(@Path("address") address: String): Completable

    @GET("v2/safes/{address}/funded/")
    fun safeFundStatus(@Path("address") address: String): Single<RelaySafeFundStatus>
}
