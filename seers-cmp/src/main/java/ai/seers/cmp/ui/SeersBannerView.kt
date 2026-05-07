package ai.seers.cmp.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.*
import android.widget.*
import ai.seers.cmp.SeersBannerPayload
import ai.seers.cmp.SeersCMP
import java.net.URL

private const val DEFAULT_BADGE_BASE64 =
    "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"
private const val DEFAULT_LOGO_URL =
    "https://seers-application-assets.s3.amazonaws.com/images/logo/seersco-logo.png"

/**
 * SeersBannerView — Native Android banner matching Flutter design exactly.
 * Supports: popup, bottom_sheet, dialog display styles.
 * All colors, font_size, button_type, layout from dashboard.
 */
class SeersBannerView(
    context: Context,
    private val payload: SeersBannerPayload,
    private val onDismiss: () -> Unit
) : FrameLayout(context) {

    private val b   = payload.banner
    private val l   = payload.language
    private val d   = payload.dialogue

    // ── Colors ──
    private val bgColor      = c(b?.bannerBgColor     ?: "#ffffff")
    private val titleColor   = c(b?.titleTextColor    ?: "#1a1a1a")
    private val bodyColor    = c(b?.bodyTextColor     ?: "#1a1a1a")
    private val agreeColor   = c(b?.agreeBtnColor     ?: "#3b6ef8")
    private val agreeText    = c(b?.agreeTextColor    ?: "#ffffff")
    private val declineColor = c(b?.disagreeBtnColor  ?: "#1a1a2e")
    private val declineText  = c(b?.disagreeTextColor ?: "#ffffff")
    private val prefBorder   = bodyColor // prefFullStyle uses body_text_color

    // ── Font size ── respect the dashboard mobile visual size, without phone-width scaling.
    private val fs        = (b?.fontSize?.toFloatOrNull() ?: 12f).coerceIn(10f, 16f)
    private val titleFs   = fs + 2f
    private val catNameFs = fs + 1f
    private val catBodyFs = fs - 1f
    private val prefFs        = fs.coerceAtLeast(12f)
    private val prefTitleFs   = prefFs + 2f
    private val prefCatNameFs = prefFs + 1f
    private val prefCatBodyFs = prefFs - 1f
    private val sfs       = fs  // alias kept for consistency
    private val fontFamily = b?.fontStyle?.lowercase()?.trim().orEmpty()
    private val bannerTypeface: Typeface = when (fontFamily) {
        "serif" -> Typeface.SERIF
        "monospace" -> Typeface.MONOSPACE
        "cursive" -> Typeface.create("casual", Typeface.NORMAL)
        "fantasy" -> Typeface.create("fantasy", Typeface.NORMAL)
        "arial", "inter", "spezia", "sans-serif" -> Typeface.SANS_SERIF
        else -> Typeface.DEFAULT
    }

    // ── Padding scale ── keep CSS-like spacing from the Vue preview.
    private val padScale: Float get() = 1f

    // ── Button type ──
    private val btnType   = b?.buttonType ?: "default"
    private val btnRadius = when { btnType.contains("rounded") -> dp(20f); btnType.contains("flat") -> 0f; else -> dp(4f) }
    private val isStroke  = btnType.contains("stroke")

    // ── Display style ──
    private val tmpl    = d?.mobileTemplate ?: "popup"
    private val layout  = b?.layout   ?: "default"
    private val position= b?.position ?: "bottom"
    private val showHandle = layout == "rounded"

    // ── Flags ──
    private val allowReject = d?.allowReject ?: true
    private val poweredBy   = d?.poweredBy   ?: true
    private val hasBadge    = d?.hasBadge    ?: false
    private val bannerTimeout = d?.bannerTimeout ?: 0
    private val showLogo    = (d?.logoStatus ?: "default") != "none"
    private val logoSrc     = d?.logoLink?.takeIf { it.isNotBlank() } ?: DEFAULT_LOGO_URL
    private val customBadgeSrc = d?.takeIf { it.badgeStatus == "custom" }?.badgeLink?.takeIf { it.isNotBlank() }

    // ── Language ──
    private val bodyText     = l?.body               ?: "We use cookies to personalize content and ads."
    private val titleText    = l?.title              ?: "We use cookies"
    private val btnAgree     = l?.btnAgreeTitle      ?: "Allow All"
    private val btnDecline   = l?.btnDisagreeTitle   ?: "Disable All"
    private val btnPref      = l?.btnPreferenceTitle ?: "Cookie settings"
    private val btnSave      = l?.btnSaveMyChoices   ?: "Save my choices"
    private val aboutCookies = l?.aboutCookies       ?: "About Our Cookies"
    private val alwaysActive = l?.alwaysActive       ?: "Always Active"

    private val cats = listOf(
        Triple("necessary",   l?.necessoryTitle  ?: "Necessary",   l?.necessoryBody  ?: "Required for the app to function. Cannot be switched off."),
        Triple("preferences", l?.preferenceTitle ?: "Preferences", l?.preferenceBody ?: "Allow the app to remember choices you make."),
        Triple("statistics",  l?.statisticsTitle ?: "Statistics",  l?.statisticsBody ?: "Help us understand how users interact with the app."),
        Triple("marketing",   l?.marketingTitle  ?: "Marketing",   l?.marketingBody  ?: "Used to track visitors and display relevant advertisements.")
    )

    // Toggle states mirror the backend dialogue defaults.
    private val toggles = mutableMapOf(
        "preferences" to (d?.preferencesChecked ?: false),
        "statistics" to (d?.statisticsChecked ?: false),
        "marketing" to (d?.targetingChecked ?: false)
    )
    private val expanded = mutableSetOf<String>()
    private var showPref = false
    private var bannerVisible = true
    private var badgeVisible = false
    private val badgeHandler = Handler(Looper.getMainLooper())
    private var badgeTimeoutRunnable: Runnable? = null
    private val defaultBadgeBitmap by lazy {
        val bytes = Base64.decode(DEFAULT_BADGE_BASE64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        build()
    }

    private fun build() {
        removeAllViews()
        val overlayVisible = showPref || bannerVisible
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = overlayVisible
        isFocusable = overlayVisible

        if (showPref) { addView(buildPrefPanel()); return }
        if (badgeVisible && hasBadge) { addView(buildBadgeView()); return }
        if (!bannerVisible) return
        when (tmpl) {
            "dialog"       -> buildDialog()
            "bottom_sheet" -> buildBottomSheet()
            else           -> buildPopup()
        }
    }

    // ══════════════════════════════════════════
    // POPUP — 3 stacked full-width buttons
    // ══════════════════════════════════════════
    private fun buildPopup() {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(bgColor, popupRadius(), topOnly = true)
            setPadding(sdp(12), sdp(12), sdp(12), sdp(12))
            elevation = dp(24f)
        }
        container.addView(bodyLabel(bodyText, sfs, 0.9f))
        container.addView(space(sdp(7)))
        container.addView(stkPrimary(btnAgree) { save("agree", true, true, true) })
        container.addView(space(sdp(5)))
        if (allowReject) { container.addView(stkDark(btnDecline) { save("disagree", false, false, false) }); container.addView(space(sdp(5))) }
        container.addView(stkOutline(btnPref) { showPreferences() })
        if (poweredBy) { container.addView(space(sdp(3))); container.addView(poweredByLabel()) }

        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        addView(container, lp)
    }

    // ══════════════════════════════════════════
    // BOTTOM SHEET — title + body + [Decline|Accept] row + Preferences full-width
    // ══════════════════════════════════════════
    private fun buildBottomSheet() {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = if (position == "top")
                roundedBg(bgColor, sheetRadius(), bottomOnly = true)
            else
                roundedBg(bgColor, sheetRadius(), topOnly = true)
            setPadding(sdp(12), sdp(12), sdp(12), sdp(12))
            elevation = dp(12f)
        }
        if (showHandle) {
            val handle = View(context).apply {
                background = roundedBg(Color.parseColor("#cccccc"), dp(2f))
                layoutParams = LinearLayout.LayoutParams(sdp(32), sdp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = sdp(6) }
            }
            container.addView(handle)
        }
        container.addView(titleLabel(titleText, titleFs))
        container.addView(space(sdp(4)))
        container.addView(bodyLabel(bodyText, sfs, 0.9f))
        container.addView(space(sdp(7)))
        // btn-row-primary: [Decline | Accept] side by side
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        if (allowReject) {
            row.addView(btnItem(btnDecline, declineColor, declineText) { save("disagree", false, false, false) }.apply {
                layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(4) }
            })
        }
        row.addView(btnItem(btnAgree, agreeColor, agreeText) { save("agree", true, true, true) }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        })
        container.addView(row)
        container.addView(space(sdp(4)))
        container.addView(prefFullBtn(btnPref) { showPreferences() })
        if (poweredBy) { container.addView(space(sdp(3))); container.addView(poweredByLabel()) }

        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
            if (position == "top") Gravity.TOP else Gravity.BOTTOM)
        addView(container, lp)
    }

    // ══════════════════════════════════════════
    // DIALOG — centered modal
    // ══════════════════════════════════════════
    private fun buildDialog() {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(bgColor, dialogRadius())
            setPadding(sdp(12), sdp(12), sdp(12), sdp(12))
            elevation = dp(24f)
        }
        container.addView(titleLabel(titleText, titleFs))
        container.addView(space(sdp(4)))
        container.addView(bodyLabel(bodyText, sfs, 0.9f))
        container.addView(space(sdp(8)))
        container.addView(stkPrimary(btnAgree) { save("agree", true, true, true) })
        container.addView(space(sdp(5)))
        if (allowReject) { container.addView(stkDark(btnDecline) { save("disagree", false, false, false) }); container.addView(space(sdp(5))) }
        container.addView(stkOutline(btnPref) { showPreferences() })

        val screenW = resources.displayMetrics.widthPixels
        val lp = LayoutParams((screenW * 0.88).toInt(), LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        addView(container, lp)
    }

    // ══════════════════════════════════════════
    // PREFERENCE PANEL — full screen
    // ══════════════════════════════════════════
    private fun buildPrefPanel(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(bgColor, dp(18f), topOnly = true)
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.92f).toInt(),
                Gravity.BOTTOM
            )
        }

        // Scrollable content
        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(sdp(12), sdp(12), sdp(12), sdp(12))
        }

        content.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = sdp(2) }

            addView(buildPreferenceLogoView() ?: View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            })

            addView(TextView(context).apply {
                text = "✕"; setTextColor(titleColor); textSize = prefFs; typeface = Typeface.create(bannerTypeface, Typeface.BOLD)
                gravity = Gravity.END
                contentDescription = "Close preferences panel"
                isFocusable = true
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
                setOnClickListener {
                    showPref = false
                    build()
                }
                layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            })
        })
        content.addView(titleLabel(aboutCookies, prefTitleFs))
        content.addView(space(sdp(4)))
        content.addView(bodyLabel(bodyText, prefFs, 0.85f))
        content.addView(space(sdp(4)))
        content.addView(TextView(context).apply {
            text = "Read Cookie Policy ↗"; setTextColor(agreeColor); textSize = prefFs
            typeface = Typeface.create(bannerTypeface, Typeface.BOLD)
            paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = sdp(6) }
        })
        content.addView(prefActionBtn(btnAgree, agreeColor, agreeText, prefFs) { save("agree", true, true, true) })
        content.addView(space(sdp(4)))
        content.addView(prefActionBtn(btnDecline, Color.parseColor("#1a1a2e"), Color.WHITE, prefFs) { save("disagree", false, false, false) })
        content.addView(space(sdp(8)))

        // Categories with divider top
        val catContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, 0)
            background = topBorderBg(Color.parseColor("#e0e0e0"), bgColor)
        }
        cats.forEach { (key, label, desc) -> catContainer.addView(buildCatRow(key, label, desc, catContainer)) }
        content.addView(catContainer)
        content.addView(space(80))

        scroll.addView(content)
        root.addView(scroll)

        // Sticky footer
        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(sdp(12), sdp(12), sdp(12), sdp(12))
            background = topBorderBg(Color.parseColor("#e0e0e0"), bgColor)
            elevation = dp(8f)
        }
        footer.addView(prefSaveBtn(btnSave, agreeColor, agreeText) {
            save("custom", toggles["preferences"]!!, toggles["statistics"]!!, toggles["marketing"]!!)
        })
        root.addView(footer)

        return root
    }

    // ── Category accordion row ──
    private fun buildCatRow(key: String, label: String, desc: String, parent: LinearLayout): LinearLayout {
        val isNec = key == "necessary"
        val wrap = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = borderedBg(Color.parseColor("#e0e0e0"), dp(5f))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = sdp(3) }
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = sdp(38)
            setPadding(sdp(10), sdp(8), sdp(10), sdp(8))
        }

        val arrow = TextView(context).apply {
            text = "▶"; setTextColor(agreeColor); textSize = (prefFs * 0.75f).coerceAtLeast(9f)
            layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { rightMargin = sdp(6) }
        }
        row.addView(arrow)

        row.addView(TextView(context).apply {
            text = label; setTextColor(bodyColor); textSize = prefCatNameFs
            typeface = Typeface.create(bannerTypeface, Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        })

        if (isNec) {
            row.addView(TextView(context).apply {
                text = alwaysActive; setTextColor(agreeColor); textSize = prefFs * 0.85f
                typeface = Typeface.create(bannerTypeface, Typeface.BOLD)
            })
        } else {
            row.addView(buildToggle(key))
        }

        val descView = TextView(context).apply {
            text = desc; setTextColor(bodyColor); textSize = prefCatBodyFs; alpha = 0.8f
            setPadding(sdp(10), sdp(8), sdp(10), sdp(9))
            background = topBorderBg(Color.parseColor("#f0f0f0"), bgColor)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        row.setOnClickListener {
            val isOpen = expanded.contains(key)
            if (isOpen) { expanded.remove(key); descView.visibility = View.GONE; arrow.rotation = 0f }
            else { expanded.add(key); descView.visibility = View.VISIBLE; arrow.rotation = 90f }
        }
        row.contentDescription = "$label. Double tap to expand or collapse details"
        row.isFocusable = true
        row.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES

        wrap.addView(row)
        wrap.addView(descView)
        return wrap
    }

    // ── Toggle switch ──
    private fun buildToggle(key: String): View {
        val togOn = toggles[key] ?: false
        val trackW = dp(36)
        val trackH = dp(20)
        val thumbSz = dp(16)
        val thumbMargin = dp(2)
        val trackRadius = trackH / 2f  // pill shape

        val toggle = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(trackW, trackH)
        }
        val track = View(context).apply {
            background = roundedBg(if (togOn) agreeColor else Color.parseColor("#cccccc"), trackRadius)
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        val thumb = View(context).apply {
            background = roundedBg(Color.WHITE, thumbSz / 2f)
            layoutParams = FrameLayout.LayoutParams(thumbSz, thumbSz,
                if (togOn) Gravity.END or Gravity.CENTER_VERTICAL
                else Gravity.START or Gravity.CENTER_VERTICAL).apply {
                marginStart = thumbMargin; marginEnd = thumbMargin
            }
        }
        toggle.addView(track); toggle.addView(thumb)
        toggle.setOnClickListener {
            val newVal = !(toggles[key] ?: false)
            toggles[key] = newVal
            track.background = roundedBg(if (newVal) agreeColor else Color.parseColor("#cccccc"), trackRadius)
            (thumb.layoutParams as FrameLayout.LayoutParams).gravity =
                if (newVal) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.START or Gravity.CENTER_VERTICAL
            thumb.requestLayout()
        }
        toggle.contentDescription = "Toggle $key cookies"
        toggle.isFocusable = true
        toggle.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        return toggle
    }

    private fun buildLogoView(): View? {
        if (!showLogo) return null

        return ImageView(context).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            minimumHeight = sdp(24)
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            loadRemoteImage(this, logoSrc)
        }
    }

    private fun buildPreferenceLogoView(): View? {
        if (!showLogo) return null

        return ImageView(context).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_START
            minimumHeight = sdp(24)
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            loadRemoteImage(this, logoSrc)
        }
    }

    private fun buildBadgeView(): View {
        val root = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        val badge = ImageView(context).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Open cookie settings"
            isFocusable = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            layoutParams = LayoutParams(sdp(34), sdp(34), Gravity.START or Gravity.BOTTOM).apply {
                marginStart = sdp(12)
                bottomMargin = sdp(12)
            }
            setOnClickListener { reopenBannerFromBadge() }
        }

        loadBadgeImage(badge)
        root.addView(badge)
        return root
    }

    private fun loadBadgeImage(imageView: ImageView) {
        if (customBadgeSrc != null) {
            loadRemoteImage(imageView, customBadgeSrc) {
                imageView.setImageBitmap(defaultBadgeBitmap)
            }
            return
        }

        imageView.setImageBitmap(defaultBadgeBitmap)
    }

    private fun loadRemoteImage(imageView: ImageView, url: String, onError: (() -> Unit)? = null) {
        Thread {
            runCatching {
                val conn = URL(url).openConnection().apply { connectTimeout = 5000; readTimeout = 5000 }
                conn.getInputStream().use { BitmapFactory.decodeStream(it) }
            }.onSuccess { bitmap ->
                imageView.post {
                    if (bitmap != null) imageView.setImageBitmap(bitmap) else onError?.invoke()
                }
            }.onFailure { imageView.post { onError?.invoke() } }
        }.start()
    }

    private fun clearBadgeTimeout() {
        badgeTimeoutRunnable?.let { badgeHandler.removeCallbacks(it) }
        badgeTimeoutRunnable = null
    }

    private fun showBadgeOnly() {
        clearBadgeTimeout()
        if (hasBadge) {
            showPref = false
            bannerVisible = false
            badgeVisible = true
            build()
            return
        }

        onDismiss()
    }

    private fun scheduleBannerTimeout() {
        if (!hasBadge || bannerTimeout <= 0) return
        clearBadgeTimeout()
        badgeTimeoutRunnable = Runnable {
            showPref = false
            bannerVisible = false
            badgeVisible = true
            build()
        }.also { badgeHandler.postDelayed(it, bannerTimeout * 1000L) }
    }

    private fun reopenBannerFromBadge() {
        clearBadgeTimeout()
        badgeVisible = false
        bannerVisible = true
        build()
        scheduleBannerTimeout()
    }

    // ─────────────────────────────────────────────────────────
    // Button builders
    // ─────────────────────────────────────────────────────────

    // stk-outline: padding:5px 8px, margin-bottom:5px, border:1.5px, font-weight:700
    private fun stkOutline(label: String, onClick: () -> Unit) = makeBtn(label, Color.TRANSPARENT, prefBorder, outline = true, marginBottom = 0, onClick = onClick)
    private fun stkDark(label: String, onClick: () -> Unit)    = makeBtn(label, declineColor, declineText, onClick = onClick)
    private fun stkPrimary(label: String, onClick: () -> Unit) = makeBtn(
        label, if (isStroke) Color.TRANSPARENT else agreeColor,
        if (isStroke) agreeColor else agreeText, outline = isStroke, onClick = onClick)
    private fun btnItem(label: String, bg: Int, fg: Int, onClick: () -> Unit) = makeBtn(label, bg, fg, padV = sdp(4), padH = sdp(4), fw = 600, marginBottom = 0, onClick = onClick)
    private fun prefFullBtn(label: String, onClick: () -> Unit) = makeBtn(label, Color.TRANSPARENT, prefBorder, outline = true, padV = sdp(4), padH = sdp(6), fw = 600, marginBottom = sdp(3), onClick = onClick)
    private fun prefActionBtn(label: String, bg: Int, fg: Int, textSize: Float = sfs, onClick: () -> Unit) = makeBtn(label, bg, fg, padV = sdp(6), padH = sdp(10), radius = dp(6f), marginBottom = 0, textSize = textSize, onClick = onClick)
    private fun prefSaveBtn(label: String, bg: Int, fg: Int, onClick: () -> Unit) = makeBtn(label, bg, fg, padV = sdp(7), padH = sdp(10), radius = dp(6f), marginBottom = 0, textSize = prefFs, onClick = onClick)

    private fun makeBtn(label: String, bg: Int, fg: Int, outline: Boolean = false,
                        padV: Int = sdp(5), padH: Int = sdp(8), fw: Int = 700,
                        marginBottom: Int = sdp(5),
                        radius: Float = btnRadius, textSize: Float = sfs, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = label; setTextColor(fg); this.textSize = textSize; isAllCaps = false
            background = if (outline) outlineBg(fg, radius) else roundedBg(bg, radius)
            setPadding(padH, padV, padH, padV)
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            includeFontPadding = false
            stateListAnimator = null
            typeface = Typeface.create(bannerTypeface, if (fw >= 700) Typeface.BOLD else Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = marginBottom
            }
            contentDescription = label
            isFocusable = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            setOnClickListener { onClick() }
        }
    }

    // ─────────────────────────────────────────────────────────
    // Text helpers
    // ─────────────────────────────────────────────────────────

    private fun titleLabel(text: String, size: Float) = TextView(context).apply {
        this.text = text; setTextColor(titleColor); textSize = size
        typeface = Typeface.create(bannerTypeface, Typeface.BOLD); setLineSpacing(0f, 1.3f)
        includeFontPadding = false
        layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    private fun bodyLabel(text: String, size: Float, alpha: Float = 1f) = TextView(context).apply {
        this.text = text; setTextColor(bodyColor); textSize = size; this.alpha = alpha; setLineSpacing(0f, 1.5f)
        typeface = Typeface.create(bannerTypeface, Typeface.NORMAL)
        includeFontPadding = false
        layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    private fun poweredByLabel() = TextView(context).apply {
        text = "Powered by Seers"; setTextColor(Color.parseColor("#aaaaaa")); textSize = sfs * 0.7f
        typeface = Typeface.create(bannerTypeface, Typeface.NORMAL)
        gravity = Gravity.CENTER
        includeFontPadding = false
        layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    private fun space(dpVal: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dpVal)
    }

    // ─────────────────────────────────────────────────────────
    // Drawing helpers
    // ─────────────────────────────────────────────────────────

    private fun dialogRadius(): Float = when {
        layout == "rounded" -> dp(20f)
        layout == "flat" -> 0f
        else -> dp(10f)
    }

    private fun sheetRadius(): Float = when {
        layout == "flat" -> 0f
        layout == "rounded" -> dp(16f)
        else -> dp(14f)
    }

    private fun popupRadius(): Float = dp(12f)

    private fun roundedBg(color: Int, radius: Float, topOnly: Boolean = false, bottomOnly: Boolean = false) = GradientDrawable().apply {
        setColor(color)
        cornerRadii = when {
            // TL TR BR BL — each needs x and y component
            topOnly    -> floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            bottomOnly -> floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius)
            else       -> floatArrayOf(radius, radius, radius, radius, radius, radius, radius, radius)
        }
    }

    private fun outlineBg(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(Color.TRANSPARENT); setStroke(dp(2), color); cornerRadius = radius
    }

    private fun borderedBg(borderColor: Int, radius: Float) = GradientDrawable().apply {
        setColor(bgColor); setStroke(dp(1), borderColor); cornerRadius = radius
    }

    /**
     * Top-border-only background using a LayerDrawable:
     *  - Layer 0 (behind): border colour, full size
     *  - Layer 1 (front) : bg colour, inset 1dp from top → exposes 1dp stripe at the top
     */
    private fun topBorderBg(borderColor: Int, bgCol: Int = Color.TRANSPARENT): Drawable {
        val border = GradientDrawable().apply { setColor(borderColor) }
        val bg     = GradientDrawable().apply { setColor(bgCol) }
        return LayerDrawable(arrayOf(border, bg)).apply {
            setLayerInset(1, 0, dp(1), 0, 0) // bg inset 1dp from top → border stripe shows
        }
    }

    private fun showPreferences() {
        clearBadgeTimeout()
        showPref = true; build()
    }

    private fun save(value: String, pref: Boolean, stat: Boolean, mkt: Boolean) {
        SeersCMP.saveConsent(value, pref, stat, mkt)
        showBadgeOnly()
    }

    override fun onDetachedFromWindow() {
        clearBadgeTimeout()
        super.onDetachedFromWindow()
    }

    private fun c(hex: String): Int = try { Color.parseColor(hex) } catch (e: Exception) { Color.BLACK }
    private fun sdp(value: Int): Int = (value * padScale * resources.displayMetrics.density).toInt()
    private fun sdp(value: Float): Float = value * padScale * resources.displayMetrics.density
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        fun show(activity: android.app.Activity, payload: SeersBannerPayload, onDismiss: () -> Unit) {
            val root = activity.window.decorView as ViewGroup
            val banner = SeersBannerView(activity, payload) {
                root.post { root.removeView(root.findViewWithTag<View>("seers_banner")) }
                onDismiss()
            }
            banner.tag = "seers_banner"
            root.addView(banner, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }
}
