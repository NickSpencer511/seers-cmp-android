package ai.seers.cmp.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.*
import android.widget.*
import ai.seers.cmp.SeersBannerPayload
import ai.seers.cmp.SeersCMP

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

    // ── Font size ──
    // Scale factor: maps Vue's 190px preview frame to real screen width
    private val screenWidthDp: Float get() {
        val dm = resources.displayMetrics
        return dm.widthPixels / dm.density
    }
    private val scale: Float get() = (screenWidthDp / 190f).coerceIn(1f, 2f)
    private val fs      = (b?.fontSize?.toFloatOrNull() ?: 14f)
    private val titleFs get() = (fs + 2f) * scale
    private val catNameFs get() = (fs + 1f) * scale
    private val catBodyFs get() = (fs - 1f) * scale
    private val sfs get() = fs * scale  // scaled font size for body/buttons

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

    // Toggle states — preferences starts checked
    private val toggles = mutableMapOf("preferences" to true, "statistics" to false, "marketing" to false)
    private val expanded = mutableSetOf<String>()
    private var showPref = false

    init {
        setBackgroundColor(Color.parseColor("#80000000"))
        build()
    }

    private fun build() {
        removeAllViews()
        if (showPref) { addView(buildPrefPanel()); return }
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
            // position == "top" → round bottom corners; else → round top corners (matches Flutter)
            background = if (position == "top")
                roundedBg(bgColor, containerRadius(), bottomOnly = true)
            else
                roundedBg(bgColor, containerRadius(), topOnly = true)
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

        val gravity = if (position == "top") Gravity.TOP else Gravity.BOTTOM
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, gravity)
        addView(container, lp)
    }

    // ══════════════════════════════════════════
    // BOTTOM SHEET — title + body + [Decline|Accept] row + Preferences full-width
    // ══════════════════════════════════════════
    private fun buildBottomSheet() {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = if (position == "top")
                roundedBg(bgColor, containerRadius(), bottomOnly = true)
            else
                roundedBg(bgColor, containerRadius(), topOnly = true)
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
            background = roundedBg(bgColor, containerRadius())
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
            background = roundedBg(bgColor, dp(16f), topOnly = true)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.88).toInt(), Gravity.BOTTOM)
        }

        // Scrollable content
        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(sdp(12), sdp(12), sdp(12), sdp(12))
        }

        content.addView(TextView(context).apply {
            text = "✕"; setTextColor(titleColor); textSize = sfs; typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.END
            setOnClickListener { onDismiss() }
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = sdp(2) }
        })
        content.addView(titleLabel(aboutCookies, titleFs))
        content.addView(space(sdp(4)))
        content.addView(bodyLabel(bodyText, sfs, 0.85f))
        content.addView(space(sdp(4)))
        content.addView(TextView(context).apply {
            text = "Read Cookie Policy ↗"; setTextColor(agreeColor); textSize = sfs
            paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = sdp(6) }
        })
        content.addView(prefActionBtn(btnAgree, agreeColor, agreeText) { save("agree", true, true, true) })
        content.addView(space(sdp(4)))
        content.addView(prefActionBtn(btnDecline, Color.parseColor("#1a1a2e"), Color.WHITE) { save("disagree", false, false, false) })
        content.addView(space(sdp(8)))

        // Categories with divider top
        val catContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, 0)
            background = topBorderBg(Color.parseColor("#e0e0e0"))
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
            setPadding(sdp(5), sdp(4), sdp(5), sdp(4))
        }

        val arrow = TextView(context).apply {
            text = "▶"; setTextColor(agreeColor); textSize = sfs * 0.75f
            layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { rightMargin = sdp(3) }
        }
        row.addView(arrow)

        row.addView(TextView(context).apply {
            text = label; setTextColor(bodyColor); textSize = catNameFs
            typeface = android.graphics.Typeface.DEFAULT
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        })

        if (isNec) {
            row.addView(TextView(context).apply {
                text = alwaysActive; setTextColor(agreeColor); textSize = sfs * 0.75f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            })
        } else {
            row.addView(buildToggle(key))
        }

        val descView = TextView(context).apply {
            text = desc; setTextColor(bodyColor); textSize = catBodyFs; alpha = 0.8f
            setPadding(sdp(7), sdp(3), sdp(7), sdp(4))
            background = topBorderBg(Color.parseColor("#f0f0f0"), Color.parseColor("#05000000"))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        row.setOnClickListener {
            val isOpen = expanded.contains(key)
            if (isOpen) { expanded.remove(key); descView.visibility = View.GONE; arrow.rotation = 0f }
            else { expanded.add(key); descView.visibility = View.VISIBLE; arrow.rotation = 90f }
        }

        wrap.addView(row)
        wrap.addView(descView)
        return wrap
    }

    // ── Toggle switch ──
    private fun buildToggle(key: String): View {
        val togOn = toggles[key] ?: false
        val toggle = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(sdp(36), sdp(20))
        }
        val track = View(context).apply {
            background = roundedBg(if (togOn) agreeColor else Color.parseColor("#cccccc"), dp(10f))
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        val thumb = View(context).apply {
            background = roundedBg(Color.WHITE, dp(8f))
            layoutParams = FrameLayout.LayoutParams(sdp(16), sdp(16), if (togOn) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.START or Gravity.CENTER_VERTICAL).apply {
                marginStart = sdp(2); marginEnd = sdp(2)
            }
        }
        toggle.addView(track); toggle.addView(thumb)
        toggle.setOnClickListener {
            val newVal = !(toggles[key] ?: false)
            toggles[key] = newVal
            track.background = roundedBg(if (newVal) agreeColor else Color.parseColor("#cccccc"), dp(10f))
            (thumb.layoutParams as FrameLayout.LayoutParams).gravity =
                if (newVal) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.START or Gravity.CENTER_VERTICAL
            thumb.requestLayout()
        }
        return toggle
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
    private fun prefActionBtn(label: String, bg: Int, fg: Int, onClick: () -> Unit) = makeBtn(label, bg, fg, padV = sdp(4), padH = sdp(6), radius = dp(4f), marginBottom = 0, onClick = onClick)
    private fun prefSaveBtn(label: String, bg: Int, fg: Int, onClick: () -> Unit) = makeBtn(label, bg, fg, padV = sdp(5), padH = sdp(6), radius = dp(4f), marginBottom = 0, onClick = onClick)

    private fun makeBtn(label: String, bg: Int, fg: Int, outline: Boolean = false,
                        padV: Int = sdp(5), padH: Int = sdp(8), fw: Int = 700,
                        marginBottom: Int = sdp(5),
                        radius: Float = btnRadius, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = label; setTextColor(fg); textSize = sfs; isAllCaps = false
            background = if (outline) outlineBg(fg, radius) else roundedBg(bg, radius)
            setPadding(padH, padV, padH, padV)
            typeface = if (fw >= 700) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = marginBottom
            }
            setOnClickListener { onClick() }
        }
    }

    // ─────────────────────────────────────────────────────────
    // Text helpers
    // ─────────────────────────────────────────────────────────

    private fun titleLabel(text: String, size: Float) = TextView(context).apply {
        this.text = text; setTextColor(titleColor); textSize = size
        typeface = android.graphics.Typeface.DEFAULT_BOLD; setLineSpacing(0f, 1.3f)
        layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    private fun bodyLabel(text: String, size: Float, alpha: Float = 1f) = TextView(context).apply {
        this.text = text; setTextColor(bodyColor); textSize = size; this.alpha = alpha; setLineSpacing(0f, 1.5f)
        layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    private fun poweredByLabel() = TextView(context).apply {
        text = "Powered by Seers"; setTextColor(Color.parseColor("#aaaaaa")); textSize = sfs * 0.7f
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    private fun space(dpVal: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dpVal)
    }

    // ─────────────────────────────────────────────────────────
    // Drawing helpers
    // ─────────────────────────────────────────────────────────

    private fun containerRadius(): Float {
        return when {
            tmpl == "dialog" -> when { layout == "rounded" -> dp(20f); layout == "flat" -> 0f; else -> dp(10f) }
            layout == "flat" -> 0f
            layout == "rounded" -> dp(16f)
            else -> dp(12f)
        }
    }

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
        setColor(Color.WHITE); setStroke(dp(1), borderColor); cornerRadius = radius
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
        showPref = true; build()
    }

    private fun save(value: String, pref: Boolean, stat: Boolean, mkt: Boolean) {
        SeersCMP.saveConsent(value, pref, stat, mkt)
        onDismiss()
    }

    private fun c(hex: String): Int = try { Color.parseColor(hex) } catch (e: Exception) { Color.BLACK }
    private fun sdp(value: Int): Int = (value * scale * resources.displayMetrics.density).toInt()
    private fun sdp(value: Float): Float = value * scale * resources.displayMetrics.density
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