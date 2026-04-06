package ai.seers.cmp

import android.content.Context
import android.content.SharedPreferences
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
    @SerializedName("value")      val value: String,       // "agree" | "disagree" | "custom"
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
// SeersCMP — Main SDK class
// ─────────────────────────────────────────────────────────────

object SeersCMP {

    private val http   = OkHttpClient()
    private val gson   = Gson()
    private val scope  = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val catMap = mapOf(3 to "statistics", 4 to "marketing", 5 to "preferences", 6 to "unclassified")

    private var settingsId: String? = null
    private var config: SeersCMPConfig? = null
    private var prefs: SharedPreferences? = null

    private var onShowBanner:      ((SeersBannerPayload) -> Unit)? = null
    private var onConsent:         ((SeersConsent, SeersConsentMap) -> Unit)? = null
    private var onConsentRestored: ((SeersConsent, SeersConsentMap) -> Unit)? = null

    /**
     * Initialize the SDK. Call once in Application.onCreate().
     *
     *     SeersCMP.initialize(
     *         context = this,
     *         settingsId = "YOUR_SDK_KEY",
     *         onShowBanner = { payload -> showConsentBanner(payload) }
     *     )
     */
    fun initialize(
        context: Context,
        settingsId: String,
        onShowBanner: ((SeersBannerPayload) -> Unit)? = null,
        onConsent: ((SeersConsent, SeersConsentMap) -> Unit)? = null,
        onConsentRestored: ((SeersConsent, SeersConsentMap) -> Unit)? = null
    ) {
        this.settingsId      = settingsId
        this.prefs           = context.getSharedPreferences("SeersCMP", Context.MODE_PRIVATE)
        this.onShowBanner    = onShowBanner
        this.onConsent       = onConsent
        this.onConsentRestored = onConsentRestored
        start()
    }

    /**
     * Check if a specific SDK should be blocked before initializing it.
     *
     *     if (SeersCMP.shouldBlock("com.google.firebase.analytics")) return
     *     FirebaseApp.initializeApp(this)
     */
    fun shouldBlock(identifier: String): Boolean = checkBlock(identifier).blocked

    /** Get full consent map — use at startup to batch-check all SDKs. */
    fun getConsentMap(): SeersConsentMap = buildConsentMap()

    /** Get stored consent — null if user hasn't consented yet. */
    fun getConsent(): SeersConsent? = settingsId?.let { loadConsent(it) }

    /** Save consent after user makes a choice. */
    fun saveConsent(value: String, preferences: Boolean, statistics: Boolean, marketing: Boolean) {
        persistConsent(value, preferences, statistics, marketing)
    }

    // ─────────────────────────────────────────────────────────
    // Private
    // ─────────────────────────────────────────────────────────

    private fun start() {
        val key = settingsId ?: return

        // Check stored consent first
        val stored = loadConsent(key)
        if (stored != null && !isExpired(stored)) {
            val map = buildConsentMap()
            onConsentRestored?.invoke(stored, map)
            return
        }

        scope.launch {
            // Fetch config
            val cfg = fetchConfig(key) ?: return@launch
            if (!cfg.eligible) return@launch
            config = cfg

            // Region check
            val region = checkRegion(key)
            if (!shouldShow(cfg.dialogue, region)) return@launch

            val lang    = resolveLanguage(cfg, region)
            val payload = SeersBannerPayload(
                dialogue   = cfg.dialogue,
                banner     = cfg.banner,
                language   = lang,
                categories = cfg.categories,
                blockList  = buildBlockList(cfg),
                regulation = region?.regulation,
                sdkKey     = key
            )
            withContext(Dispatchers.Main) { onShowBanner?.invoke(payload) }
        }
    }

    private fun fetchConfig(sdkKey: String): SeersCMPConfig? {
        val urls = listOf(
            "https://cdn.seersco.com/mobile/configs/$sdkKey.json",
            "${config?.cxHost ?: ""}/api/mobile/sdk/config/$sdkKey"
        )
        for (url in urls) {
            try {
                val resp = http.newCall(Request.Builder().url(url).build()).execute()
                if (resp.isSuccessful) {
                    return gson.fromJson(resp.body?.string(), SeersCMPConfig::class.java)
                }
            } catch (e: Exception) { /* try next */ }
        }
        return null
    }

    private fun checkRegion(sdkKey: String): SeersRegion? {
        val host = config?.cxHost ?: return null
        return try {
            val resp = http.newCall(Request.Builder().url("$host/api/mobile/sdk/$sdkKey").build()).execute()
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

        val cats = listOf(
            "statistics"   to list.statistics,
            "marketing"    to list.marketing,
            "preferences"  to list.preferences,
            "unclassified" to list.unclassified
        )
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
            timestamp = Date().toString(),
            expiry    = cal.time.toString()
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
        return try {
            val expiry = Date(consent.expiry)
            Date().after(expiry)
        } catch (e: Exception) { true }
    }

    private fun logConsent(sdkKey: String, consent: SeersConsent) {
        val host = config?.cxHost ?: return
        try {
            val body = JSONObject().apply {
                put("sdk_key",  sdkKey)
                put("platform", config?.platform ?: "android")
                put("consent",  consent.value)
                put("categories", JSONObject().apply {
                    put("necessary",   consent.necessary)
                    put("preferences", consent.preferences)
                    put("statistics",  consent.statistics)
                    put("marketing",   consent.marketing)
                })
                put("timestamp", consent.timestamp)
            }.toString()

            val req = Request.Builder()
                .url("$host/api/mobile/sdk/save-consent")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute()
        } catch (e: Exception) { /* silent */ }
    }

    private fun shouldShow(dialogue: SeersCMPDialogue?, region: SeersRegion?): Boolean {
        if (dialogue == null) return false
        return if (dialogue.regionDetection) {
            region?.eligible == true && region.regulation != "none"
        } else true
    }

    private fun resolveLanguage(config: SeersCMPConfig, region: SeersRegion?): SeersCMPLanguage? {
        if (config.language != null) return config.language
        val code = region?.data?.countryIsoCode ?: config.dialogue?.defaultLanguage ?: "GB"
        return config.languages?.firstOrNull { it.countryCode == code } ?: config.languages?.firstOrNull()
    }
}

// ─────────────────────────────────────────────────────────────
// Config Models
// ─────────────────────────────────────────────────────────────

data class SeersCMPConfig(
    @SerializedName("eligible")          val eligible: Boolean = false,
    @SerializedName("sdk_key")           val sdkKey: String? = null,
    @SerializedName("platform")          val platform: String? = null,
    @SerializedName("cx_host")           val cxHost: String? = null,
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
    @SerializedName("agreement_expire")  val agreementExpire: Int = 365,
    @SerializedName("default_language")  val defaultLanguage: String? = null,
    @SerializedName("banner_bg_color")   val bannerBgColor: String? = null,
    @SerializedName("agree_btn_color")   val agreeBtnColor: String? = null,
    @SerializedName("agree_text_color")  val agreeTextColor: String? = null,
    @SerializedName("disagree_btn_color")  val disagreeBtnColor: String? = null,
    @SerializedName("disagree_text_color") val disagreeTextColor: String? = null,
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
    @SerializedName("font_style")             val fontStyle: String? = null,
    @SerializedName("font_size")              val fontSize: String? = null,
    @SerializedName("button_type")            val buttonType: String? = null,
    @SerializedName("layout")                 val layout: String? = null,
    @SerializedName("position")               val position: String? = null
)

data class SeersCMPLanguage(
    @SerializedName("country_code")        val countryCode: String? = null,
    @SerializedName("title")               val title: String? = null,
    @SerializedName("body")                val body: String? = null,
    @SerializedName("btn_agree_title")     val btnAgreeTitle: String? = null,
    @SerializedName("btn_disagree_title")  val btnDisagreeTitle: String? = null,
    @SerializedName("btn_preference_title") val btnPreferenceTitle: String? = null,
    @SerializedName("btn_save_my_choices") val btnSaveMyChoices: String? = null,
    @SerializedName("about_cookies")       val aboutCookies: String? = null,
    @SerializedName("necessory_title")     val necessoryTitle: String? = null,
    @SerializedName("preference_title")    val preferenceTitle: String? = null,
    @SerializedName("statistics_title")    val statisticsTitle: String? = null,
    @SerializedName("marketing_title")     val marketingTitle: String? = null
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
