package ai.seers.cmp

import ai.seers.cmp.ui.SeersBannerView
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Locale
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

// ─────────────────────────────────────────────────────────────
// IAB TCF v2.3
// ─────────────────────────────────────────────────────────────

/**
 * Stores IAB TCF v2.3 consent signals in SharedPreferences using the standard IABTCF_* keys.
 * Spec: https://github.com/InteractiveAdvertisingBureau/GDPR-Transparency-and-Consent-Framework
 */
// Seers CMP registered ID — shared between SeersIABTCF and SeersCMP
private const val SEERS_CMP_ID      = 158
private const val SEERS_CMP_VERSION = 1

object SeersIABTCF {

    private val statisticsPurposes  = listOf(7, 8, 9)
    private val marketingPurposes   = listOf(1, 2, 3, 4)
    private val preferencesPurposes = listOf(5, 6)
    private val BASE64URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun store(
        prefs: SharedPreferences,
        necessary: Boolean,
        preferences: Boolean,
        statistics: Boolean,
        marketing: Boolean,
        cmpId: Int = SEERS_CMP_ID,
        cmpVersion: Int = SEERS_CMP_VERSION
    ) {
        val purposeConsents = Array(10) { "0" }
        purposeConsents[0] = "1" // Necessary (purpose 1) always on
        if (marketing)   { marketingPurposes.forEach   { if (it <= 10) purposeConsents[it-1] = "1" } }
        if (statistics)  { statisticsPurposes.forEach  { if (it <= 10) purposeConsents[it-1] = "1" } }
        if (preferences) { preferencesPurposes.forEach { if (it <= 10) purposeConsents[it-1] = "1" } }
        val pcStr = purposeConsents.joinToString("")

        val purposeLI = Array(10) { "0" }
        if (statistics)  { purposeLI[6] = "1"; purposeLI[7] = "1" }
        if (preferences) { purposeLI[5] = "1" }
        val liStr = purposeLI.joinToString("")

        prefs.edit().apply {
            putInt("IABTCF_CmpSdkID",                      cmpId)
            putInt("IABTCF_CmpSdkVersion",                 cmpVersion)
            putInt("IABTCF_PolicyVersion",                 1)
            putInt("IABTCF_gdprApplies",                   1)
            putInt("IABTCF_UseNonStandardTexts",           1)
            putString("IABTCF_PurposeConsents",            pcStr)
            putString("IABTCF_PurposeLegitimateInterests", liStr)
            putString("IABTCF_SpecialFeaturesOptIns",      "00")
            putString("IABTCF_VendorConsents",             "")
            putString("IABTCF_VendorLegitimateInterests",  "")
            putString("IABTCF_PublisherConsent",           pcStr)
            putString("IABTCF_PublisherLegitimateInterests", liStr)
            putString("IABTCF_TCString",                   buildTCString(purposeConsents, cmpId, cmpVersion))
            putLong("IABTCF_ConsentTimestamp",             System.currentTimeMillis() / 1000)
        }.apply()
    }

    fun getTCData(prefs: SharedPreferences): Map<String, Any> = mapOf(
        "tcString"                   to (prefs.getString("IABTCF_TCString", "") ?: ""),
        "cmpId"                      to prefs.getInt("IABTCF_CmpSdkID", 0),
        "cmpVersion"                 to prefs.getInt("IABTCF_CmpSdkVersion", 0),
        "gdprApplies"                to (prefs.getInt("IABTCF_gdprApplies", 0) == 1),
        "purposeConsents"            to (prefs.getString("IABTCF_PurposeConsents", "") ?: ""),
        "purposeLegitimateInterests" to (prefs.getString("IABTCF_PurposeLegitimateInterests", "") ?: ""),
        "specialFeaturesOptIns"      to (prefs.getString("IABTCF_SpecialFeaturesOptIns", "") ?: ""),
        "vendorConsents"             to (prefs.getString("IABTCF_VendorConsents", "") ?: ""),
        "publisherConsent"           to (prefs.getString("IABTCF_PublisherConsent", "") ?: ""),
        "consentTimestamp"           to prefs.getLong("IABTCF_ConsentTimestamp", 0L),
    )

    fun clear(prefs: SharedPreferences) {
        listOf(
            "IABTCF_CmpSdkID", "IABTCF_CmpSdkVersion", "IABTCF_PolicyVersion",
            "IABTCF_gdprApplies", "IABTCF_UseNonStandardTexts", "IABTCF_PurposeConsents",
            "IABTCF_PurposeLegitimateInterests", "IABTCF_SpecialFeaturesOptIns",
            "IABTCF_VendorConsents", "IABTCF_VendorLegitimateInterests",
            "IABTCF_PublisherConsent", "IABTCF_PublisherLegitimateInterests",
            "IABTCF_TCString", "IABTCF_ConsentTimestamp",
        ).forEach { prefs.edit().remove(it).apply() }
    }

    private fun buildTCString(purposeConsents: Array<String>, cmpId: Int, cmpVersion: Int): String {
        val now = System.currentTimeMillis() / 100L // deciseconds as Long — avoids Int overflow
        var bits = ""
        bits += intToBits(2,          6)   // version
        bits += longToBits(now,       36)  // created  — must be Long (36-bit field)
        bits += longToBits(now,       36)  // lastUpdated
        bits += intToBits(cmpId,      12)
        bits += intToBits(cmpVersion, 12)
        bits += intToBits(0,          6)   // consentScreen
        bits += langToBits("EN")
        bits += intToBits(48,         12)  // vendorListVersion
        bits += intToBits(4,          6)   // tcfPolicyVersion
        bits += "0"                        // isServiceSpecific
        bits += "0"                        // useNonStandardTexts
        bits += "0".repeat(12)             // specialFeatureOptIns
        bits += purposeConsents.joinToString("") + "0".repeat(14) // purposeConsents 24 bits
        bits += "0".repeat(24)             // purposeLegitimateInterests
        bits += "0"                        // purposeOneTreatment
        bits += langToBits("AA")           // publisherCC
        bits += intToBits(0, 16) + "0"    // vendorConsents maxId + encoding
        bits += intToBits(0, 16) + "0"    // vendorLI maxId + encoding
        bits += intToBits(0, 12)           // numRestrictions
        return base64urlEncode(bits)
    }

    // Use Long for fields > 31 bits to prevent CWE-190 integer overflow
    private fun longToBits(value: Long, length: Int): String {
        val bin = java.lang.Long.toBinaryString(value)
        val padded = bin.padStart(length, '0')
        return padded.takeLast(length)
    }

    private fun intToBits(value: Int, length: Int): String {
        val bin = Integer.toBinaryString(value)
        val padded = bin.padStart(length, '0')
        return padded.takeLast(length)
    }

    private fun langToBits(lang: String): String {
        val upper = lang.uppercase()
        val a = (upper.getOrNull(0)?.code ?: 65) - 65
        val b = (upper.getOrNull(1)?.code ?: 65) - 65
        return intToBits(a.coerceAtLeast(0), 6) + intToBits(b.coerceAtLeast(0), 6)
    }

    private fun base64urlEncode(bits: String): String {
        val padded = bits + "0".repeat((6 - bits.length % 6) % 6)
        val sb = StringBuilder()
        var i = 0
        while (i < padded.length) {
            val chunk = padded.substring(i, i + 6)
            val idx = chunk.toInt(2)
            if (idx < BASE64URL.length) sb.append(BASE64URL[idx])
            i += 6
        }
        return sb.toString()
    }
}

// ─────────────────────────────────────────────────────────────
// Models
// ─────────────────────────────────────────────────────────────

data class SeersConsent(
    @SerializedName("sdk_key")    val sdkKey: String,
    @SerializedName("value")      val value: String,
    @SerializedName("necessary")  val necessary: Boolean = true,
    @SerializedName("preferences")val preferences: Boolean = false,
    @SerializedName("statistics") val statistics: Boolean = false,
    @SerializedName("marketing")  val marketing: Boolean = false,
    @SerializedName("do_not_sell") val doNotSell: Boolean = false,
    @SerializedName("privacy_signals") val privacySignals: Map<String, Any>? = null,
    @SerializedName("timestamp")  val timestamp: String,
    @SerializedName("expiry")     val expiry: String
)

data class SeersConsentMap(
    val statistics:   SeersCategory,
    val marketing:    SeersCategory,
    val preferences:  SeersCategory,
    val unclassified: SeersCategory
)

data class SeersCategory(val allowed: Boolean, val sdks: List<String>)
data class SeersBlockResult(val blocked: Boolean, val category: String?)

data class SeersBannerPayload(
    val dialogue:   SeersCMPDialogue?,
    val banner:     SeersCMPBanner?,
    val language:   SeersCMPLanguage?,
    val categories: List<SeersCMPCategory>?,
    val privacyFrameworks: Map<String, Any>,
    val blockList:  SeersBlockList,
    val regulation: String?,
    val sdkKey:     String
)

data class SeersBlockList(
    val statistics:   MutableList<String> = mutableListOf(),
    val marketing:    MutableList<String> = mutableListOf(),
    val preferences:  MutableList<String> = mutableListOf(),
    val unclassified: MutableList<String> = mutableListOf()
)

// ─────────────────────────────────────────────────────────────
// SeersCMP
// ─────────────────────────────────────────────────────────────

object SeersCMP {

    // Google Consent Mode v2 developer ID
    const val SEERS_GOOGLE_DEV_ID = "dNmU0M2"

    // Allowlist of trusted Seers hosts — prevents SSRF (CWE-918)
    private val ALLOWED_HOSTS = setOf("consents.dev", "seers.ai", "seersco.com", "cdn.consents.dev", "cdn.seersco.com")

    private fun isAllowedHost(urlString: String): Boolean {
        return try {
            val host = java.net.URL(urlString).host ?: return false
            ALLOWED_HOSTS.any { h -> host == h || host.endsWith(".$h") }
        } catch (e: Exception) { false }
    }

    private val http   = OkHttpClient()
    private val gson   = Gson()
    private val scope  = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val catMap = mapOf(3 to "statistics", 4 to "marketing", 5 to "preferences", 6 to "unclassified")

    private var settingsId: String? = null
    private var config: SeersCMPConfig? = null
    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null

    var lastPayload: SeersBannerPayload? = null
        private set

    /** Optional: set your app package name for security verification */
    var appId: String? = null

    private var onShowBanner:      ((SeersBannerPayload) -> Unit)? = null
    private var onConsent:         ((SeersConsent, SeersConsentMap) -> Unit)? = null
    private var onConsentRestored: ((SeersConsent, SeersConsentMap) -> Unit)? = null

    fun initialize(
        context: Context,
        settingsId: String,
        onShowBanner: ((SeersBannerPayload) -> Unit)? = null,
        onConsent: ((SeersConsent, SeersConsentMap) -> Unit)? = null,
        onConsentRestored: ((SeersConsent, SeersConsentMap) -> Unit)? = null
    ) {
        this.settingsId      = settingsId
        this.appContext      = context.applicationContext
        this.prefs           = createEncryptedPrefs(context)
        this.onShowBanner    = onShowBanner
        this.onConsent       = onConsent
        this.onConsentRestored = onConsentRestored
        start()
    }

    fun shouldBlock(identifier: String): Boolean = checkBlock(identifier).blocked

    /** Regulation type: "gdpr" | "ccpa" | "none" */
    val regulation: String get() = lastPayload?.regulation ?: "gdpr"
    val isGdpr: Boolean get() = regulation == "gdpr"
    val isCcpa: Boolean get() = regulation == "ccpa"
    val isNone: Boolean get() = regulation == "none"

    /**
     * Call BEFORE initialising any third-party SDK.
     * GDPR (region_selection 1|3) → pre-block until consent given.
     * CCPA (region_selection 2)   → NOT pre-blocked; block only after explicit opt-out.
     * none (region_selection 0)   → never block.
     *
     * Example:
     *   if (!SeersCMP.shouldBlockNow("com.google.firebase.analytics")) {
     *       FirebaseApp.initializeApp(this)
     *   }
     */
    fun shouldBlockNow(identifier: String): Boolean {
        if (isNone) return false
        val stored = settingsId?.let { loadConsent(it) }
        // Consent already given — check per-category
        if (stored != null && !isExpired(stored)) {
            return checkBlockWithConsent(identifier, stored)
        }
        // No consent yet:
        return if (isGdpr) checkBlock(identifier).blocked else false
    }

    private fun checkBlockWithConsent(identifier: String, consent: SeersConsent): Boolean {
        val result = checkBlock(identifier)
        if (!result.blocked) return false
        return when (result.category) {
            "statistics"  -> !consent.statistics
            "marketing"   -> !consent.marketing
            "preferences" -> !consent.preferences
            else          -> false
        }
    }
    fun getConsentMap(): SeersConsentMap = buildConsentMap()
    fun getConsent(): SeersConsent? = settingsId?.let { loadConsent(it) }
    fun getPrivacyFrameworks(): Map<String, Any> = buildPrivacyFrameworks()
    fun frameworkEnabled(key: String): Boolean = frameworkValue(key).let {
        it == true || (it is Map<*, *> && it["enabled"] == true)
    }
    fun getConsentSignals(
        value: String = "custom",
        preferences: Boolean = false,
        statistics: Boolean = false,
        marketing: Boolean = false,
        doNotSell: Boolean? = null,
        attStatus: String? = null
    ): Map<String, Any?> = buildConsentSignals(value, preferences, statistics, marketing, doNotSell, attStatus)
    fun saveConsent(value: String, preferences: Boolean, statistics: Boolean, marketing: Boolean, doNotSell: Boolean? = null, attStatus: String? = null) {
        persistConsent(value, preferences, statistics, marketing, doNotSell, attStatus)
    }

    private fun start() {
        val key = settingsId ?: return
        val stored = loadConsent(key)
        if (stored != null && !isExpired(stored)) {
            val map = buildConsentMap()
            onConsentRestored?.invoke(stored, map)
            retryQueuedConsentIfNeeded()
            return
        }
        scope.launch {
            val ts = System.currentTimeMillis() / 60000
            val cfg = fetchConfig(key, ts) ?: return@launch
            if (!cfg.eligible) return@launch
            config = cfg
            val appIdHeader = appId
            if (appIdHeader != null) {
                val registeredId = cfg.bundleId ?: cfg.packageName
                if (registeredId != null && !appIdHeader.equals(registeredId, ignoreCase = true)) return@launch
            }
            val region = checkRegion(key)
            if (!shouldShow(cfg.dialogue, region)) return@launch
            val lang = resolveLanguage(cfg, region)
            val payload = SeersBannerPayload(
                dialogue = cfg.dialogue, banner = cfg.banner, language = lang,
                categories = cfg.categories, privacyFrameworks = getPrivacyFrameworks(), blockList = buildBlockList(cfg),
                regulation = region?.regulation, sdkKey = key
            )
            lastPayload = payload
            withContext(Dispatchers.Main) {
                if (onShowBanner != null) onShowBanner?.invoke(payload) else autoShowBanner(payload)
            }
        }
    }

    private fun retryQueuedConsentIfNeeded() {
        val key = settingsId ?: return
        if (prefs?.getString("SeersConsentQueue_$key", null) == null) return
        scheduleRetry(key, attempt = 1)
    }

    private fun scheduleRetry(sdkKey: String, attempt: Int) {
        if (attempt > 5) return
        val delayMs = (Math.pow(2.0, attempt.toDouble()) * 1000).toLong()
        scope.launch {
            delay(delayMs)
            val json = prefs?.getString("SeersConsentQueue_$sdkKey", null) ?: return@launch
            val consent = try { gson.fromJson(json, SeersConsent::class.java) } catch (e: Exception) { return@launch }
            logConsent(sdkKey, consent)
            delay(1000)
            if (prefs?.getString("SeersConsentQueue_$sdkKey", null) != null) {
                scheduleRetry(sdkKey, attempt + 1)
            }
        }
    }

    private fun autoShowBanner(payload: SeersBannerPayload) {
        val app = appContext as? Application ?: return
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                app.unregisterActivityLifecycleCallbacks(this)
                SeersBannerView.show(activity, payload) { activity.window.decorView.let {
                    (it as? ViewGroup)?.removeView(it.findViewWithTag("seers_banner"))
                }}
            }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }

    private fun fetchConfig(sdkKey: String, ts: Long): SeersCMPConfig? {
        val url = "https://cdn.seersco.com/mobile/configs/$sdkKey.json?v=$ts"
        return try {
            val resp = http.newCall(Request.Builder().url(url).build()).execute()
            when {
                resp.code == 404 -> SeersCMPConfig(eligible = false)
                resp.isSuccessful -> {
                    val body = resp.body?.string()
                    val config = gson.fromJson(body, SeersCMPConfig::class.java)
                    // Cache on success
                    prefs?.edit()?.putString("SeersConfig_$sdkKey", body)?.apply()
                    config
                }
                else -> loadCachedConfig(sdkKey)
            }
        } catch (e: Exception) {
            // Network failed — use cached config
            loadCachedConfig(sdkKey)
        }
    }

    private fun loadCachedConfig(sdkKey: String): SeersCMPConfig? {
        val json = prefs?.getString("SeersConfig_$sdkKey", null) ?: return null
        return try { gson.fromJson(json, SeersCMPConfig::class.java) } catch (e: Exception) { null }
    }

    private fun checkRegion(sdkKey: String): SeersRegion? {
        val host = config?.cxHost ?: return null
        if (!isAllowedHost(host)) return null
        return try {
            val headers = Request.Builder().url("$host/api/mobile/sdk/$sdkKey")
            appId?.let { headers.addHeader("X-App-ID", it) }
            val resp = http.newCall(headers.build()).execute()
            if (resp.isSuccessful) gson.fromJson(resp.body?.string(), SeersRegion::class.java) else null
        } catch (e: Exception) { null }
    }

    private fun buildBlockList(cfg: SeersCMPConfig? = null): SeersBlockList {
        val c = cfg ?: config ?: return SeersBlockList()
        val list = SeersBlockList()
        if (c.blockingMode == "none" || c.blockingDomains.isNullOrEmpty()) return list
        for (item in c.blockingDomains) {
            val identifier = if (c.blockingMode == "prior_consent") item.d else item.src
            val catId      = if (c.blockingMode == "prior_consent") item.c else item.category
            val cat        = catMap[catId] ?: "unclassified"
            identifier?.let {
                when (cat) {
                    "statistics"   -> list.statistics.add(it)
                    "marketing"    -> list.marketing.add(it)
                    "preferences"  -> list.preferences.add(it)
                    else           -> list.unclassified.add(it)
                }
            }
        }
        return list
    }

    private fun checkBlock(identifier: String): SeersBlockResult {
        val key     = settingsId ?: return SeersBlockResult(false, null)
        val consent = loadConsent(key)
        val list    = buildBlockList()
        val id      = identifier.lowercase()
        val cats    = listOf("statistics" to list.statistics, "marketing" to list.marketing,
                             "preferences" to list.preferences, "unclassified" to list.unclassified)
        for ((cat, sdks) in cats) {
            for (sdk in sdks) {
                if (id.contains(sdk.lowercase())) {
                    val allowed = when (cat) {
                        "statistics"  -> consent?.statistics  ?: false
                        "marketing"   -> consent?.marketing   ?: false
                        "preferences" -> consent?.preferences ?: false
                        else          -> false
                    }
                    return SeersBlockResult(!allowed, cat)
                }
            }
        }
        return SeersBlockResult(false, null)
    }

    private fun buildConsentMap(): SeersConsentMap {
        val key     = settingsId ?: return emptyConsentMap()
        val consent = loadConsent(key)
        val list    = buildBlockList()
        return SeersConsentMap(
            statistics   = SeersCategory(consent?.statistics  ?: false, list.statistics),
            marketing    = SeersCategory(consent?.marketing   ?: false, list.marketing),
            preferences  = SeersCategory(consent?.preferences ?: false, list.preferences),
            unclassified = SeersCategory(false,                          list.unclassified)
        )
    }

    private fun emptyConsentMap() = SeersConsentMap(
        SeersCategory(false, emptyList()), SeersCategory(false, emptyList()),
        SeersCategory(false, emptyList()), SeersCategory(false, emptyList())
    )

    private fun persistConsent(value: String, pref: Boolean, stat: Boolean, mkt: Boolean, doNotSell: Boolean? = null, attStatus: String? = null) {
        val key    = settingsId ?: return
        val expire = config?.dialogue?.agreementExpire ?: 365
        val privacySignals = buildConsentSignals(value, pref, stat, mkt, doNotSell, attStatus)
        val consent = SeersConsent(
            sdkKey = key, value = value, necessary = true,
            preferences = pref, statistics = stat, marketing = mkt,
            doNotSell = privacySignals["universalOptOut"].let { it is Map<*, *> && it["doNotSell"] == true },
            privacySignals = privacySignals.filterValues { it != null } as Map<String, Any>,
            timestamp = Instant.now().toString(), expiry = Instant.now().plusSeconds(expire * 86400L).toString()
        )
        prefs?.edit()?.putString("SeersConsent_$key", gson.toJson(consent))?.apply()
        // Store IAB TCF v2.3 keys (only if enabled in dashboard)
        if (config?.dialogue?.enableIabTcf == true) {
            prefs?.let { SeersIABTCF.store(it, necessary = true, preferences = pref, statistics = stat, marketing = mkt) }
        }
        scope.launch { logConsent(key, consent) }
        val map = buildConsentMap()
        onConsent?.invoke(consent, map)
    }

    private fun loadConsent(sdkKey: String): SeersConsent? {
        val json = prefs?.getString("SeersConsent_$sdkKey", null) ?: return null
        return try { gson.fromJson(json, SeersConsent::class.java) } catch (e: Exception) { null }
    }

    private fun isExpired(consent: SeersConsent): Boolean {
        return try {
            Instant.now().isAfter(Instant.parse(consent.expiry))
        } catch (e: Exception) { true }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "SeersCMP",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to standard prefs if EncryptedSharedPreferences unavailable
            context.getSharedPreferences("SeersCMP", Context.MODE_PRIVATE)
        }
    }

    /**
     * Returns a stable anonymous device ID scoped to this sdk_key.
     * Generated once, stored in EncryptedSharedPreferences, never changes.
     * Used for MAU deduplication — not linked to any PII.
     */
    private fun getOrCreateDeviceId(sdkKey: String): String {
        val key = "SeersDeviceId_$sdkKey"
        val existing = prefs?.getString(key, null)
        if (!existing.isNullOrBlank()) return existing
        val newId = java.util.UUID.randomUUID().toString()
        prefs?.edit()?.putString(key, newId)?.apply()
        return newId
    }

    private fun logConsent(sdkKey: String, consent: SeersConsent) {
        val host = config?.cxHost ?: run { queueConsent(sdkKey, consent); return }
        if (!isAllowedHost(host)) { queueConsent(sdkKey, consent); return }
        try {
            val body = JSONObject().apply {
                put("sdk_key",    sdkKey)
                put("platform",   config?.platform ?: "android")
                put("consent",    consent.value)
                put("categories", JSONObject().apply {
                    put("necessary",   consent.necessary)
                    put("preferences", consent.preferences)
                    put("statistics",  consent.statistics)
                    put("marketing",   consent.marketing)
                })
                put("do_not_sell", consent.doNotSell)
                put("privacy_signals", JSONObject(gson.toJson(consent.privacySignals)))
                put("timestamp",   consent.timestamp)
                // Stable anonymous device ID for MAU deduplication — not PII
                put("device_id",   getOrCreateDeviceId(sdkKey))
                appVersion?.let { put("app_version", it) }
                userEmail?.let  { put("email",       it) }
            }.toString()
            val req = Request.Builder().url("$host/api/mobile/sdk/save-consent")
                .post(body.toRequestBody("application/json".toMediaType())).build()
            val resp = http.newCall(req).execute()
            if (resp.isSuccessful) clearQueuedConsent(sdkKey) else queueConsent(sdkKey, consent)
        } catch (e: Exception) {
            queueConsent(sdkKey, consent)
        }
    }

    private fun queueConsent(sdkKey: String, consent: SeersConsent) {
        prefs?.edit()?.putString("SeersConsentQueue_$sdkKey", gson.toJson(consent))?.apply()
    }

    private fun clearQueuedConsent(sdkKey: String) {
        prefs?.edit()?.remove("SeersConsentQueue_$sdkKey")?.apply()
    }

    /** Call when app regains connectivity to flush any queued consent log. */
    fun retryQueuedConsent() {
        val key = settingsId ?: return
        val json = prefs?.getString("SeersConsentQueue_$key", null) ?: return
        val consent = try { gson.fromJson(json, SeersConsent::class.java) } catch (e: Exception) { return }
        scope.launch { logConsent(key, consent) }
    }

    /** Returns IAB TCF v2.3 consent data (IABTCF_* keys).
     *  Use this to pass TCF data to ad SDKs that require it. */
    fun getTCData(): Map<String, Any> = prefs?.let { SeersIABTCF.getTCData(it) } ?: emptyMap()

    /** Optional: set app version for consent log enrichment.
     *   SeersCMP.appVersion = BuildConfig.VERSION_NAME */
    var appVersion: String? = null

    /** Optional: set user email for consent log enrichment.
     *   SeersCMP.userEmail = "user@example.com" */
    var userEmail: String? = null

    private fun buildPrivacyFrameworks(): Map<String, Any> {
        val d = config?.dialogue
        val platform = config?.platform
        return mapOf(
            "google_consent_mode_v2" to mapOf("enabled" to (d?.applyGoogleConsent == true)),
            "iab_tcf" to mapOf("enabled" to (d?.enableIabTcf == true), "version" to "2.3"),
            "apple_att" to mapOf("enabled" to (d?.appleAtt == true), "applies" to listOf("ios", "both", "react_native", "flutter").contains(platform)),
            "google_play_disclosure" to mapOf("enabled" to (d?.googlePlayDisclosure == true), "applies" to listOf("android", "both", "react_native", "flutter").contains(platform)),
            "universal_opt_out" to mapOf("enabled" to (d?.universalOptOut == true), "signal" to "do_not_sell_or_share"),
            "conditional" to mapOf(
                "gpp" to (d?.enableGpp == true),
                "microsoft_clarity" to (d?.microsoftClarityConsent == true),
                "meta_facebook_sdk" to (d?.metaSdkConsent == true),
                "microsoft_ads" to (d?.microsoftAdsConsent == true),
                "amazon_ads" to (d?.amazonAdsConsent == true),
            )
        )
    }

    private fun frameworkValue(key: String): Any? {
        var node: Any? = buildPrivacyFrameworks()
        for (part in key.split(".")) {
            node = (node as? Map<*, *>)?.get(part) ?: return null
        }
        return node
    }

    private fun buildConsentSignals(
        value: String,
        preferences: Boolean,
        statistics: Boolean,
        marketing: Boolean,
        doNotSell: Boolean?,
        attStatus: String?
    ): Map<String, Any?> {
        val frameworks = buildPrivacyFrameworks()
        val optOut = doNotSell ?: (value == "disagree")
        fun section(name: String): Map<*, *> = frameworks[name] as? Map<*, *> ?: emptyMap<String, Any>()
        return mapOf(
            "appleATT" to mapOf("enabled" to (section("apple_att")["enabled"] == true), "applies" to (section("apple_att")["applies"] == true), "status" to attStatus),
            "googlePlayDisclosure" to mapOf("enabled" to (section("google_play_disclosure")["enabled"] == true), "applies" to (section("google_play_disclosure")["applies"] == true)),
            "googleConsentModeV2" to mapOf(
                "enabled" to (section("google_consent_mode_v2")["enabled"] == true),
                "analytics_storage" to if (statistics) "granted" else "denied",
                "ad_storage" to if (marketing) "granted" else "denied",
                "ad_user_data" to if (marketing) "granted" else "denied",
                "ad_personalization" to if (marketing) "granted" else "denied",
            ),
            "iabTCF" to mapOf("enabled" to (section("iab_tcf")["enabled"] == true), "version" to (section("iab_tcf")["version"] ?: "2.3")),
            "universalOptOut" to mapOf("enabled" to (section("universal_opt_out")["enabled"] == true), "signal" to (section("universal_opt_out")["signal"] ?: "do_not_sell_or_share"), "doNotSell" to optOut),
            "conditional" to (frameworks["conditional"] as? Map<*, *> ?: emptyMap<String, Any>()),
        )
    }

    private fun shouldShow(dialogue: SeersCMPDialogue?, region: SeersRegion?): Boolean {
        if (dialogue == null) return false
        // region_selection=0 → never show banner
        if (dialogue.regionSelection == 0) return false
        return if (dialogue.regionDetection) region?.eligible == true && region.regulation != "none" else true
    }

    private fun resolveLanguage(config: SeersCMPConfig, region: SeersRegion?): SeersCMPLanguage? {
        if (config.language != null) return config.language
        val code = region?.data?.countryIsoCode ?: config.dialogue?.defaultLanguage ?: "GB"
        return config.languages?.firstOrNull { it.countryCode == code } ?: config.languages?.firstOrNull()
    }
}



// ─────────────────────────────────────────────────────────────
// Config Models — added missing language fields
// ─────────────────────────────────────────────────────────────

data class SeersCMPConfig(
    @SerializedName("eligible")          val eligible: Boolean = false,
    @SerializedName("sdk_key")           val sdkKey: String? = null,
    @SerializedName("platform")          val platform: String? = null,
    @SerializedName("cx_host")           val cxHost: String? = null,
    @SerializedName("bundle_id")         val bundleId: String? = null,
    @SerializedName("package_name")      val packageName: String? = null,
    @SerializedName("dialogue")          val dialogue: SeersCMPDialogue? = null,
    @SerializedName("banner")            val banner: SeersCMPBanner? = null,
    @SerializedName("languages")         val languages: List<SeersCMPLanguage>? = null,
    @SerializedName("language")          val language: SeersCMPLanguage? = null,
    @SerializedName("categories")        val categories: List<SeersCMPCategory>? = null,
    @SerializedName("blocking_mode")     val blockingMode: String? = null,
    @SerializedName("blocking_domains")  val blockingDomains: List<SeersCMPBlockDomain>? = null
)

data class SeersCMPDialogue(
    @SerializedName("region_detection")  val regionDetection: Boolean = false,
    @SerializedName("region_selection")  val regionSelectionRaw: Any? = null,
    @SerializedName("agreement_expire")  val agreementExpire: Int = 365,
    @SerializedName("default_language")  val defaultLanguage: String? = null,
    @SerializedName("preferences_checked") val preferencesChecked: Boolean = false,
    @SerializedName("statistics_checked")  val statisticsChecked: Boolean = false,
    @SerializedName("targeting_checked")   val targetingChecked: Boolean = false,
    @SerializedName("allow_reject")      val allowReject: Boolean = true,
    @SerializedName("powered_by")        val poweredBy: Boolean = true,
    @SerializedName("mobile_template")   val mobileTemplate: String? = null,
    @SerializedName("has_badge")         val hasBadge: Boolean = false,
    @SerializedName("badge_link")        val badgeLink: String? = null,
    @SerializedName("badge_status")      val badgeStatus: String? = null,
    @SerializedName("logo_link")         val logoLink: String? = null,
    @SerializedName("logo_status")       val logoStatus: String? = null,
    @SerializedName("banner_timeout")    val bannerTimeout: Int = 0,
    @SerializedName("enable_iab_tcf")    val enableIabTcf: Boolean = false,
    @SerializedName("apply_google_consent") val applyGoogleConsent: Boolean = false,
    @SerializedName("apple_att") val appleAtt: Boolean = false,
    @SerializedName("google_play_disclosure") val googlePlayDisclosure: Boolean = false,
    @SerializedName("universal_opt_out") val universalOptOut: Boolean = false,
    @SerializedName("enable_gpp") val enableGpp: Boolean = false,
    @SerializedName("microsoft_clarity_consent") val microsoftClarityConsent: Boolean = false,
    @SerializedName("meta_sdk_consent") val metaSdkConsent: Boolean = false,
    @SerializedName("microsoft_ads_consent") val microsoftAdsConsent: Boolean = false,
    @SerializedName("amazon_ads_consent") val amazonAdsConsent: Boolean = false
) {
    val regionSelection: Int get() = when (regionSelectionRaw) {
        is Double -> regionSelectionRaw.toInt()
        is Int    -> regionSelectionRaw
        is String -> regionSelectionRaw.toIntOrNull() ?: 1
        else      -> 1
    }
}

data class SeersCMPBanner(
    @SerializedName("banner_bg_color")        val bannerBgColor: String? = null,
    @SerializedName("agree_btn_color")        val agreeBtnColor: String? = null,
    @SerializedName("agree_text_color")       val agreeTextColor: String? = null,
    @SerializedName("disagree_btn_color")     val disagreeBtnColor: String? = null,
    @SerializedName("disagree_text_color")    val disagreeTextColor: String? = null,
    @SerializedName("preferences_btn_color")  val preferencesBtnColor: String? = null,
    @SerializedName("preferences_text_color") val preferencesTextColor: String? = null,
    @SerializedName("title_text_color")       val titleTextColor: String? = null,
    @SerializedName("body_text_color")        val bodyTextColor: String? = null,
    @SerializedName("font_size")              val fontSize: String? = null,
    @SerializedName("font_style")             val fontStyle: String? = null,
    @SerializedName("button_type")            val buttonType: String? = null,
    @SerializedName("layout")                 val layout: String? = null,
    @SerializedName("position")               val position: String? = null
)

data class SeersCMPLanguage(
    @SerializedName("country_code")         val countryCode: String? = null,
    @SerializedName("title")                val title: String? = null,
    @SerializedName("body")                 val body: String? = null,
    @SerializedName("btn_agree_title")      val btnAgreeTitle: String? = null,
    @SerializedName("btn_disagree_title")   val btnDisagreeTitle: String? = null,
    @SerializedName("btn_preference_title") val btnPreferenceTitle: String? = null,
    @SerializedName("btn_save_my_choices")  val btnSaveMyChoices: String? = null,
    @SerializedName("about_cookies")        val aboutCookies: String? = null,
    @SerializedName("always_active")        val alwaysActive: String? = null,
    @SerializedName("necessory_title")      val necessoryTitle: String? = null,
    @SerializedName("necessory_body")       val necessoryBody: String? = null,
    @SerializedName("preference_title")     val preferenceTitle: String? = null,
    @SerializedName("preference_body")      val preferenceBody: String? = null,
    @SerializedName("statistics_title")     val statisticsTitle: String? = null,
    @SerializedName("statistics_body")      val statisticsBody: String? = null,
    @SerializedName("marketing_title")      val marketingTitle: String? = null,
    @SerializedName("marketing_body")       val marketingBody: String? = null
)

data class SeersCMPCategory(
    @SerializedName("id")                val id: Int? = null,
    @SerializedName("customize_title")   val customizeTitle: String? = null,
    @SerializedName("customize_body")    val customizeBody: String? = null,
    @SerializedName("customize_checked") val customizeChecked: Boolean? = null
)

data class SeersCMPBlockDomain(
    @SerializedName("src")      val src: String? = null,
    @SerializedName("category") val category: Int? = null,
    @SerializedName("d")        val d: String? = null,
    @SerializedName("c")        val c: Int? = null
)

data class SeersRegion(
    @SerializedName("eligible")         val eligible: Boolean? = null,
    @SerializedName("regulation")       val regulation: String? = null,
    @SerializedName("region_selection") val regionSelection: Int? = null,
    @SerializedName("data")             val data: SeersGeoData? = null
)

data class SeersGeoData(
    @SerializedName("country_iso_code") val countryIsoCode: String? = null,
    @SerializedName("country_name")     val countryName: String? = null
)
