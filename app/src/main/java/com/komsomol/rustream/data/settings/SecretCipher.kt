package com.komsomol.rustream.data.settings

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Шифрование чувствительных строк (куки сессий) ключом из Android Keystore.
 * Ключ не покидает защищённое хранилище системы и недоступен другим приложениям.
 *
 * Обратная совместимость: [decrypt] возвращает строку как есть, если у неё нет
 * нашего префикса — значит она сохранена старой версией в открытом виде.
 * При следующем сохранении она будет зашифрована. Так уже выполненные входы
 * в аккаунты не теряются после обновления приложения.
 */
@Singleton
class SecretCipher @Inject constructor() {

    private companion object {
        const val TAG = "SecretCipher"
        const val KEY_ALIAS = "rustream_secrets_v1"
        const val PREFIX = "enc1:"          // маркер зашифрованного значения
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12               // GCM: 96-битный вектор инициализации
        const val TAG_BITS = 128
    }

    /** Зашифровать; при любой ошибке вернуть исходную строку (лучше, чем потерять данные) */
    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return plain
        return try {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val iv = cipher.iv
            val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            PREFIX + Base64.encodeToString(iv + body, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.w(TAG, "encrypt failed, storing as-is: " + e.message)
            plain
        }
    }

    /** Расшифровать; значения без префикса — «legacy», отдаём как есть */
    fun decrypt(stored: String): String {
        if (!stored.startsWith(PREFIX)) return stored
        return try {
            val raw = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(
                Cipher.DECRYPT_MODE, secretKey(),
                GCMParameterSpec(TAG_BITS, raw, 0, IV_LEN)
            )
            String(cipher.doFinal(raw, IV_LEN, raw.size - IV_LEN), Charsets.UTF_8)
        } catch (e: Exception) {
            // Ключ пропал (сброс/переустановка) — считаем, что данных нет:
            // приложение просто попросит войти заново
            Log.w(TAG, "decrypt failed: " + e.message)
            ""
        }
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return gen.generateKey()
    }
}
