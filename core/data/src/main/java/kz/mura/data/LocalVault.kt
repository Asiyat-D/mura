package kz.mura.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Non-exportable per-installation AES key. No plaintext keys are written to disk. */
class LocalVault {
    private val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    @Synchronized private fun key(): SecretKey {
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    @Synchronized fun seal(text: String, context: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(context.toByteArray(Charsets.UTF_8))
        val clear = text.toByteArray(Charsets.UTF_8)
        return try { Base64.encodeToString(cipher.iv + cipher.doFinal(clear), Base64.NO_WRAP) } finally { clear.fill(0) }
    }
    @Synchronized fun open(value: String, context: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        // Do not generate a replacement key when existing ciphertext cannot be opened.
        val existing = store.getKey(ALIAS, null) as? SecretKey ?: error("Vault key unavailable")
        cipher.init(Cipher.DECRYPT_MODE, existing, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(context.toByteArray(Charsets.UTF_8))
        val clear = cipher.doFinal(bytes.copyOfRange(12, bytes.size))
        return try { String(clear, Charsets.UTF_8) } finally { clear.fill(0) }
    }
    @Synchronized fun rotate() { store.deleteEntry(ALIAS); key() }
    companion object { private const val ALIAS = "mura.local.v1" }
}
