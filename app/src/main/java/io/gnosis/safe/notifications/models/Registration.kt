package io.gnosis.safe.notifications.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.gnosis.data.utils.toSignatureString
import pm.gnosis.crypto.KeyPair
import pm.gnosis.crypto.utils.Sha3Utils
import pm.gnosis.utils.addHexPrefix
import pm.gnosis.utils.hexToByteArray
import pm.gnosis.utils.toHexString

@JsonClass(generateAdapter = true)
data class Registration(
    @Json(name = "uuid") val uuid: String,
    @Json(name = "cloudMessagingToken") val cloudMessagingToken: String,
    @Json(name = "buildNumber") val buildNumber: String,
    @Json(name = "bundle") val bundle: String,
    @Json(name = "deviceType") val deviceType: String = "ANDROID",
    @Json(name = "version") val version: String,
    @Json(name = "timestamp") val timestamp: String? = null,
    @Json(name = "safeRegistrations") val chainRegistrations: MutableList<ChainData> = mutableListOf()
) {

    fun hashForSafes(safes: List<String>): String {

        val stringToHash = StringBuilder().apply {
            append(PREFIX)
            append(timestamp)
            append(uuid)
            append(cloudMessagingToken)
            safes.forEach {
                append(it)
            }
        }.toString()

        return Sha3Utils.keccak(stringToHash.toByteArray()).toHexString().addHexPrefix()
    }

    fun addRegistrationData(data: ChainData) {
        chainRegistrations.add(data)
    }

    @JsonClass(generateAdapter = true)
    data class ChainData(
        @Json(name = "chainId") val chainId: String,
        @Json(name = "safes") val safes: List<String>,
        @Json(name = "signatures") val signatures: MutableList<String> = mutableListOf()
    ) {

        fun buildAndAddSignature(registrationHashForChain: String, key: ByteArray) {
            val signature =
                KeyPair
                    .fromPrivate(key)
                    .sign(registrationHashForChain.hexToByteArray())
                    .toSignatureString()
                    .addHexPrefix()
            addSignature(signature)
        }

        fun addSignature(signature: String) {
            signatures.add(signature)
        }
    }

    companion object {
        private const val PREFIX = "gnosis-safe"
    }
}
