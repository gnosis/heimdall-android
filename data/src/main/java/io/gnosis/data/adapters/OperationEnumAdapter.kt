package io.gnosis.data.adapters

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.ToJson
import io.gnosis.data.models.transaction.Operation

class OperationEnumAdapter {
    @ToJson
    fun toJson(operation: Operation): Int = operation.id

    @FromJson
    fun fromJson(operation: String): Operation =
        when (operation) {
            "0" -> Operation.CALL
            "1" -> Operation.DELEGATE
            else -> throw JsonDataException("Unsupported operation value: \"$operation\"")
        }
}
