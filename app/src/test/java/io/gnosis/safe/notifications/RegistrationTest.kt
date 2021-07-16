package io.gnosis.safe.notifications

import io.gnosis.data.models.Chain
import io.gnosis.safe.notifications.models.Registration
import io.gnosis.safe.utils.MnemonicKeyAndAddressDerivator
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import org.junit.Test
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import pm.gnosis.mnemonic.Bip39Generator
import pm.gnosis.mnemonic.wordlists.WordListProvider

class RegistrationTest {

    private val wordListProvider: WordListProvider = mockk()

    @Test
    fun testHash() {

        val  safes = listOf(SAFE_1, SAFE_2)

        val registration = Registration(
            uuid = UUID,
            cloudMessagingToken = CLOUD_MESSAGING_TOKEN,
            buildNumber = BUILD_NUMBER,
            bundle = BUNDLE,
            deviceType = DEVICE_TYPE,
            version = VERSION,
            timestamp = TIMESTAMP
        )

        assertEquals("0xc1faf614797d0ab4bb011163bf5166c3694f19042b260427b69ba9c69a4076e5", registration.hashForSafes(safes))
    }

    @Test
    fun testSignature() {

        val derivator = MnemonicKeyAndAddressDerivator(Bip39Generator(wordListProvider))
        derivator.initialize("display bless asset brother fish sauce lyrics grit friend online tumble useless")

        val key = derivator.keyForIndex(0)
        val address = derivator.addressesForPage(0, 1)[0]

        assertEquals("0x8Cd8D40103B500fc80bb2D1709bB8b23C8BdaF87", address.asEthereumAddressChecksumString())

        val  safes = listOf(SAFE_1, SAFE_2)

        val registration = Registration(
            uuid = UUID,
            cloudMessagingToken = CLOUD_MESSAGING_TOKEN,
            buildNumber = BUILD_NUMBER,
            bundle = BUNDLE,
            deviceType = DEVICE_TYPE,
            version = VERSION,
            timestamp = TIMESTAMP
        )

        val registrationHashForChain = registration.hashForSafes(safes)

        assertEquals("0xc1faf614797d0ab4bb011163bf5166c3694f19042b260427b69ba9c69a4076e5", registrationHashForChain)

        val chainRegistrationData = Registration.ChainData(
            Chain.ID_MAINNET.toString(),
            safes
        )

        chainRegistrationData.buildAndAddSignature(registrationHashForChain, key.toByteArray())

        assertEquals(
            "0x77a687a3e0021202c4d542a6aeccdb0a22bdcb722892d3a5082334d2c72468771a1e7aa303925a0115a09789c36a2a1e7bb5feb212bbd4db7c9f0c1ab01739291b",
            chainRegistrationData.signatures[0]
        )
    }

    companion object {
        const val UUID = "33971c4e-fb98-4e18-a08d-13c881ae292a"
        const val SAFE_1 = "0x4dEBDD6CEe25b2F931D2FE265D70e1a533B02453"
        const val SAFE_2 = "0x72ac1760daF52986421b1552BdCa04707E78950e"
        const val CLOUD_MESSAGING_TOKEN =
            "dSh5Se1XgEiTiY-4cv1ixY:APA91bG3vYjy9VgB3X3u5EsBphJABchb8Xgg2cOSSekPsxDsfE5xyBeu6gKY0wNhbJHgQUQQGocrHx0Shbx6JMFx2VOyhJx079AduN01NWD1-WjQerY5s3l-cLnHoNNn8fJfARqSUb3G"
        const val BUNDLE = "io.gnosis.multisig.prod.mainnet"
        const val VERSION = "2.7.0"
        const val DEVICE_TYPE = "IOS"
        const val BUILD_NUMBER = "199"
        const val TIMESTAMP = "1607013002"
    }
}
