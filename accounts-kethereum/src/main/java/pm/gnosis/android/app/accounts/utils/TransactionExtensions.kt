package pm.gnosis.android.app.accounts.utils

import org.kethereum.functions.rlp.RLPElement
import org.kethereum.functions.rlp.RLPList
import org.kethereum.functions.rlp.encode
import org.kethereum.functions.rlp.toRLP
import pm.gnosis.android.app.accounts.models.Transaction
import pm.gnosis.crypto.ECDSASignature
import pm.gnosis.crypto.utils.Sha3Utils

fun Transaction.rlp(signature: ECDSASignature? = null): ByteArray {
    val items = ArrayList<RLPElement>()
    items.add(nonce.toRLP())
    items.add(gasPrice.toRLP())
    items.add(adjustedStartGas.toRLP())
    items.add(to.toRLP())
    items.add(value.toRLP())
    items.add(data.toRLP())

    if (signature != null) {
        items.add(adjustV(signature.v).toRLP())
        items.add(signature.r.toRLP())
        items.add(signature.s.toRLP())
    } else if (chainId > 0) {
        items.add(chainId.toRLP())
        items.add(0.toRLP())
        items.add(0.toRLP())
    }

    return RLPList(items).encode()
}

fun Transaction.hash(): ByteArray {
    return Sha3Utils.keccak(rlp())
}

private fun Transaction.adjustV(v: Byte): Byte {
    if (chainId > 0) {
        return (v.toInt() + 8 + 2 * chainId).toByte()
    }
    return v
}