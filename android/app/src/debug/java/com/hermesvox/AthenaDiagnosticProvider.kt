package com.hermesvox

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import com.hermesvox.core.HermesSession
import org.json.JSONObject
import java.net.URI

/** USB diagnostics for the debug build. Caller access requires the shell DUMP permission. */
class AthenaDiagnosticProvider : ContentProvider() {
    private val app get() = requireNotNull(context)
    private val prefs get() = app.getSharedPreferences("hv", Context.MODE_PRIVATE)
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        // ContentProvider.call does not enforce manifest read/write permissions.
        app.enforceCallingOrSelfPermission(Manifest.permission.DUMP, "ADB required")
        val result = when (method) {
            "status" -> snapshot()
            "probe", "turn" -> probe(method == "turn")
            "speech" -> AthenaSpeechCheck.run(app)
            "enableLocalVoice" -> {
                check(prefs.edit().putBoolean(LocalVoiceRouting.PREF, true)
                    .putBoolean(AlphaGate.KEY_MASTER, true)
                    .putBoolean(AlphaGate.FeatureSet.PHONE_TOOLS.key, true)
                    .putString(ModelCatalog.KEY_STT_BACKEND, ModelCatalog.BACKEND_ONDEVICE)
                    .putString("tts", "piper").commit())
                val sourceActions = listOf("timer", "volume")
                for (action in sourceActions) {
                    PhoneGrantStore.set(app, requireNotNull(PhoneGrants.groupOf(action)), PhoneGrants.State.ALLOW)
                }
                snapshot()
            }
            else -> throw IllegalArgumentException("Unknown diagnostic")
        }
        return Bundle().apply { putString("report", result.toString()) }
    }

    private fun snapshot(): JSONObject {
        val grants = PhoneGrantStore.source(app)
        val key = prefs.getString("key", "").orEmpty()
        return JSONObject().apply {
            put("version", PresenceDiagnosticCollect.versionName(app))
            put("microphone_granted", app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            put("gateway_configured", !prefs.getString("url", "").isNullOrBlank())
            put("gateway_key_encrypted", SecureStore.isVersioned(key) && !SecureStore.decrypt(key).isNullOrBlank())
            put("preview_enabled", prefs.getBoolean(LocalVoiceRouting.PREF, LocalVoiceRouting.DEFAULT_ENABLED))
            put("phone_tools_enabled", AlphaGate.allows(app, AlphaGate.FeatureSet.PHONE_TOOLS))
            put("timer_grant", PhoneGrants.stateOf(grants, "timer")?.id)
            put("volume_grant", PhoneGrants.stateOf(grants, "volume")?.id)
            put("on_device_stt_selected", prefs.getString(ModelCatalog.KEY_STT_BACKEND, ModelCatalog.BACKEND_ONDEVICE) == ModelCatalog.BACKEND_ONDEVICE)
            put("piper_selected", prefs.getString("tts", ModelCatalog.defaultTts(app)) == "piper")
            put("models", JSONObject().apply {
                for (spec in ModelCatalog.required) put(spec.id, ModelCatalog.isInstalled(app, spec.id))
            })
        }
    }

    private fun probe(turn: Boolean): JSONObject {
        val url = prefs.getString("url", "").orEmpty()
        val key = SecureStore.decrypt(prefs.getString("key", "").orEmpty())
        val status = if (url.isBlank() || key.isNullOrBlank()) "unconfigured" else try {
            val session = HermesSession(url, key, prefs.getString("model", "hermes-agent").orEmpty())
            if (turn) {
                val reply = session.turnStored("Voice diagnostic. Reply with just VOX_OK. Do not use tools.")
                if (reply.trim().trimEnd('.').equals("VOX_OK", ignoreCase = true)) "ok" else "unexpected_reply"
            } else {
                val models = JSONObject(session.ping()).optJSONArray("data")
                if (models != null && models.length() > 0) "ok" else "unexpected_response"
            }
        } catch (e: Exception) {
            // HTTP errors can contain a response body or URL. Return only their class.
            "failed_" + e.javaClass.simpleName
        }
        return JSONObject().put(if (turn) "synthetic_gateway_turn" else "gateway_models_get", status)
    }

    /** The HTTPS endpoint and bearer arrive on stdin. Never put the bearer in an intent or file. */
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        app.enforceCallingOrSelfPermission(Manifest.permission.DUMP, "ADB required")
        require(uri.path == "/gateway" && mode == "w")
        val (reader, writer) = ParcelFileDescriptor.createReliablePipe()
        Thread {
            ParcelFileDescriptor.AutoCloseInputStream(reader).bufferedReader().use { input ->
                runCatching {
                    val url = requireNotNull(input.readLine())
                    val key = requireNotNull(input.readLine())
                    val target = URI(url)
                    require(target.scheme == "https" && target.host != null && target.userInfo == null &&
                        target.query == null && target.fragment == null && key.isNotBlank() && input.readLine() == null)
                    val sealed = requireNotNull(SecureStore.encrypt(key))
                    check(SecureStore.decrypt(sealed) == key)
                    check(prefs.edit().putString("url", url).putString("key", sealed).commit())
                }.onFailure { runCatching { reader.closeWithError("Pairing failed") } }
            }
        }.start()
        return writer
    }

    override fun getType(uri: Uri): String? = null
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = throw UnsupportedOperationException()
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = throw UnsupportedOperationException()
}
