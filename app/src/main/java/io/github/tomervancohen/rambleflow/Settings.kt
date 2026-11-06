package io.github.tomervancohen.rambleflow

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Private preferences carry no raw key; backups/transfer are explicitly disabled. */
class Settings(context: Context) {
    private val localeManager = if (android.os.Build.VERSION.SDK_INT >= 33)
        context.getSystemService(android.app.LocaleManager::class.java) else null
    private val prefs = context.getSharedPreferences("ramble", Context.MODE_PRIVATE)
    var step: Int
        get() = prefs.getInt("step", 0)
        set(v) {
            prefs.edit().putInt("step", v).commit()
        }

    var removedSourceTrees: Set<String>
        get() = prefs.getStringSet("removedSourceTrees", emptySet())!!.toSet()
        set(v) { prefs.edit().putStringSet("removedSourceTrees", v).commit() }

    var transcriptionLanguage: String
        get() = prefs.getString("transcriptionLanguage", if (java.util.Locale.getDefault().language in setOf("he","iw")) "he" else "en")!!
        set(v) { prefs.edit().putString("transcriptionLanguage", v).commit() }

    fun initializeAudioRetention() {
        if (!prefs.contains("keepAudio")) prefs.edit().putBoolean("keepAudio",prefs.contains("step")).commit()
    }
    var keepAudio: Boolean
        get() = prefs.getBoolean("keepAudio",true)
        set(value) { prefs.edit().putBoolean("keepAudio",value).commit() }

    var quickPending: String
        get() = prefs.getString("quickPending", "")!!
        set(v) { prefs.edit().putString("quickPending", v).commit() }
    var trialRecording: String
        get() = prefs.getString("trialRecording", "")!!
        set(v) {
            prefs.edit().putString("trialRecording", v).commit()
        }

    var provider: String
        get() = prefs.getString("provider", "groq")!!
        set(v) {
            prefs.edit().putString("provider", v).commit()
        }

    var paused: Boolean
        get() = prefs.getBoolean("paused", false)
        set(v) {
            prefs.edit().putBoolean("paused", v).commit()
        }

    var wifiOnly: Boolean
        get() = prefs.getBoolean("wifi", false)
        set(v) {
            prefs.edit().putBoolean("wifi", v).commit()
        }

    var driveFolder: String
        get() = prefs.getString("driveFolder", "")!!
        set(v) {
            prefs.edit().putString("driveFolder", v).commit()
        }

    var driveFolderName: String
        get() =
            prefs.getString(
                "driveFolderName",
                "Ramble",
            )!!
        set(v) {
            prefs.edit().putString("driveFolderName", v).commit()
        }

    var drivePendingFolder: String
        get() = prefs.getString("pendingFolder", "")!!
        set(v) {
            prefs.edit().putString("pendingFolder", v).commit()
        }

    var driveAccount: String
        get() = prefs.getString("driveAccount", "")!!
        set(v) {
            prefs.edit().putString("driveAccount", v).commit()
        }

    var driveEnabled: Boolean
        get() = prefs.getBoolean("driveEnabled", false)
        set(v) {
            prefs.edit().putBoolean("driveEnabled", v).commit()
        }

    var appLanguage: String
        get() = if (android.os.Build.VERSION.SDK_INT >= 33 && localeManager != null)
            localeManager.applicationLocales.let { if (it.isEmpty) "" else it[0].language }
            else prefs.getString("appLanguage", "")!!
        set(v) { require(v in setOf("", "en", "he")); prefs.edit().putString("appLanguage", v).commit() }

    fun resetPromptLanguage() { prefs.edit().remove("promptLanguage").commit() }

    var driveFolders: Set<String>
        get() = prefs.getStringSet("driveFolders", emptySet())!!.toSet() + listOf(driveFolder).filter { it.isNotBlank() }
        set(v) { prefs.edit().putStringSet("driveFolders", v).commit() }

    var promptEnglish: Boolean
        get() = prefs.getString("promptLanguage", null)?.let { it == "en" }
            ?: (java.util.Locale.getDefault().language !in setOf("he", "iw"))
        set(v) {
            prefs.edit().putString("promptLanguage", if (v) "en" else "he").commit()
        }

    var lastScan: Long
        get() = prefs.getLong("lastScan", 0)
        set(v) {
            prefs.edit().putLong("lastScan", v).commit()
        }

    var error: String
        get() = prefs.getString("error", "")!!
        set(v) {
            prefs.edit().putString("error", v).commit()
        }

    var outputTree: String
        get() = prefs.getString("outputTree", "")!!
        set(v) {
            prefs.edit().putString("outputTree", v).commit()
        }

    var tree: String
        get() = prefs.getString("tree", "")!!
        set(v) {
            prefs.edit().putString("tree", v).commit()
        }

    fun profile(provider: String) = prefs.getString("profile.$provider", provider)!!

    fun hasKey(provider: String) = prefs.contains("key.${profile(provider)}")

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("ramble.credentials.v1", null) as? SecretKey)
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(
                    KeyGenParameterSpec.Builder(
                            "ramble.credentials.v1",
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
                generateKey()
            }
    }

    private fun encryptKey(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }

    /** An encrypted draft survives navigation/restarts without becoming a usable credential. */
    fun saveKeyDraft(provider: String, value: String) {
        require(value.isNotBlank())
        check(prefs.edit().putString("draftKey.$provider", encryptKey(value)).commit())
    }

    fun clearKeyDraft(provider: String) {
        check(prefs.edit().remove("draftKey.$provider").commit())
    }

    fun readKeyDraft(provider: String): String {
        val encoded = prefs.getString("draftKey.$provider", null) ?: return ""
        return runCatching { decryptKey(encoded) }.getOrDefault("")
    }

    fun saveKey(provider: String, value: String) {
        val encoded = encryptKey(value)
        val profile = provider + "@" + java.util.UUID.randomUUID().toString()
        check(
            prefs
                .edit()
                .putString("key.$profile", encoded)
                .putString("profile.$provider", profile)
                .remove("draftKey.$provider")
                .commit()
        )
    }

    fun readKey(provider: String): String {
        return decryptKey(prefs.getString("key.$provider", null) ?: throw IllegalStateException("key"))
    }

    private fun decryptKey(encoded: String): String {
        val data =
            Base64.decode(
                encoded,
                Base64.NO_WRAP,
            )
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
        return cipher.doFinal(data.copyOfRange(12, data.size)).toString(Charsets.UTF_8)
    }
}
