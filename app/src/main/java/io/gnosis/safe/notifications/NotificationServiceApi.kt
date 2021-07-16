package io.gnosis.safe.notifications

import io.gnosis.data.BuildConfig
import io.gnosis.safe.notifications.models.Registration
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Path
import java.math.BigInteger

interface NotificationServiceApi {

    @POST("/v1/register/notifications")
    suspend fun register(@Body data: Registration)

    @DELETE("/v1/chains/{chainId}/notifications/devices/{uuid}/safes/{address}")
    suspend fun unregisterSafe(@Path("uuid") deviceUuid: String, @Path("chainId") chainId: BigInteger, @Path("address") safe: String)

    companion object {
        const val BASE_URL = BuildConfig.CLIENT_GATEWAY_URL
    }
}
