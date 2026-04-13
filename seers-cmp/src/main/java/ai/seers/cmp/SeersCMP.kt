package ai.seers.cmp

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
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
import java.util.*

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
        this.prefs           = context.getSharedPreferences("SeersCMP", Context.MODE_PRIVATE)
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
    fun saveConsent(value: String, preferences: Boolean, statistics: Boolean, marketing: Boolean) {
        persistConsent(value, preferences, statistics, marketing)
    }

    private fun start() {
        val key = settingsId ?: return
        val stored = loadConsent(key)
        if (stored != null && !isExpired(stored)) {
            val map = buildConsentMap()
            onConsentRestored?.invoke(stored, map)
            return
        }
        scope.launch {
            // Cache-busting: changes every minute so deleted configs aren't served from CDN cache
            val ts = System.currentTimeMillis() / 60000
            val cfg = fetchConfig(key, ts) ?: return@launch
            if (!cfg.eligible) return@launch
            config = cfg

            // App identity verification
            val appIdHeader = appId
            if (appIdHeader != null) {
                val registeredId = cfg.bundleId ?: cfg.packageName
                if (registeredId != null && !appIdHeader.equals(registeredId, ignoreCase = true)) {
                    return@launch
                }
            }

            val region = checkRegion(key)
            if (!shouldShow(cfg.dialogue, region)) return@launch

            val lang = resolveLanguage(cfg, region)
            val payload = SeersBannerPayload(
                dialogue   = cfg.dialogue,
                banner     = cfg.banner,
                language   = lang,
                categories = cfg.categories,
                blockList  = buildBlockList(cfg),
                regulation = region?.regulation,
                sdkKey     = key
            )
            lastPayload = payload
            withContext(Dispatchers.Main) {
                if (onShowBanner != null) {
                    onShowBanner?.invoke(payload)
                } else {
                    // Auto-show banner if no callback provided
                    autoShowBanner(payload)
                }
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
        val url = "https://cdn.consents.dev/mobile/configs/$sdkKey.json?v=$ts"
        return try {
            val resp = http.newCall(Request.Builder().url(url).build()).execute()
            when {
                resp.code == 404 -> SeersCMPConfig(eligible = false)
                resp.isSuccessful -> gson.fromJson(resp.body?.string(), SeersCMPConfig::class.java)
                else -> null
            }
        } catch (e: Exception) { null }
    }

    private fun checkRegion(sdkKey: String): SeersRegion? {
        val host = config?.cxHost ?: return null
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

    private fun persistConsent(value: String, pref: Boolean, stat: Boolean, mkt: Boolean) {
        val key    = settingsId ?: return
        val expire = config?.dialogue?.agreementExpire ?: 365
        val cal    = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, expire) }
        val consent = SeersConsent(
            sdkKey = key, value = value, necessary = true,
            preferences = pref, statistics = stat, marketing = mkt,
            timestamp = Date().toString(), expiry = cal.time.toString()
        )
        prefs?.edit()?.putString("SeersConsent_$key", gson.toJson(consent))?.apply()
        scope.launch { logConsent(key, consent) }
        val map = buildConsentMap()
        onConsent?.invoke(consent, map)
    }

    private fun loadConsent(sdkKey: String): SeersConsent? {
        val json = prefs?.getString("SeersConsent_$sdkKey", null) ?: return null
        return try { gson.fromJson(json, SeersConsent::class.java) } catch (e: Exception) { null }
    }

    private fun isExpired(consent: SeersConsent): Boolean {
        return try { Date().after(Date(consent.expiry)) } catch (e: Exception) { true }
    }

    private fun logConsent(sdkKey: String, consent: SeersConsent) {
        val host = config?.cxHost ?: return
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
                put("timestamp",   consent.timestamp)
                appVersion?.let { put("app_version", it) }
                userEmail?.let  { put("email",       it) }
            }.toString()
            val req = Request.Builder().url("$host/api/mobile/sdk/save-consent")
                .post(body.toRequestBody("application/json".toMediaType())).build()
            http.newCall(req).execute()
        } catch (e: Exception) {}
    }

    /** Optional: set app version for consent log enrichment.
     *   SeersCMP.appVersion = BuildConfig.VERSION_NAME */
    var appVersion: String? = null

    /** Optional: set user email for consent log enrichment.
     *   SeersCMP.userEmail = "user@example.com" */
    var userEmail: String? = null

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
    @SerializedName("region_selection")  val regionSelection: Int = 1,
    @SerializedName("agreement_expire")  val agreementExpire: Int = 365,
    @SerializedName("default_language")  val defaultLanguage: String? = null,
    @SerializedName("allow_reject")      val allowReject: Boolean = true,
    @SerializedName("powered_by")        val poweredBy: Boolean = true,
    @SerializedName("mobile_template")   val mobileTemplate: String? = null
)

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
    @SerializedName("eligible")   val eligible: Boolean? = null,
    @SerializedName("regulation") val regulation: String? = null,
    @SerializedName("data")       val data: SeersGeoData? = null
)

data class SeersGeoData(
    @SerializedName("country_iso_code") val countryIsoCode: String? = null,
    @SerializedName("country_name")     val countryName: String? = null
)
