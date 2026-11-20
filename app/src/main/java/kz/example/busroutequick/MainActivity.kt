package kz.example.busroutequick

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** MVP: полностью локальный справочник и стандартный Android ACTION_VIEW. */
class MainActivity : Activity() {
    private val bg = Color.rgb(242, 243, 245)
    private val ink = Color.rgb(26, 28, 32)
    private val subdued = Color.rgb(95, 101, 114) // >= 4.5:1 on bg and on the recent card
    private val yellow = Color.rgb(255, 204, 0)
    private val track = Color.rgb(228, 229, 233)
    // Recent card is deliberately pale so it does not compete with the number and keypad.
    private val card = Color.rgb(234, 235, 239)
    private val chip = Color.rgb(248, 248, 250)
    private val chipStroke = Color.rgb(222, 224, 228)
    private val chipText = Color.rgb(88, 94, 106)
    private val paleYellow = Color.rgb(255, 232, 140)
    private val white = Color.WHITE

    private var number = ""
    private var transport = Transport.BUS
    private lateinit var entries: List<RouteEntry>
    private lateinit var numberView: TextView
    private lateinit var clearNumberButton: ImageView
    private lateinit var hintView: TextView
    private lateinit var openButton: LinearLayout
    private lateinit var openLabel: TextView
    private lateinit var busButton: LinearLayout
    private lateinit var trolleyButton: LinearLayout
    private lateinit var recentSection: LinearLayout
    private var recentRoutes: List<RecentRoute> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        entries = runCatching {
            assets.open("routes.tsv").bufferedReader().use { RouteCatalog.parseTsv(it.readText()) }
        }.getOrDefault(emptyList())
        recentRoutes = RouteHistory.decode(
            getSharedPreferences("recent_routes", MODE_PRIVATE).getString("items", null)
        )
        number = savedInstanceState?.getString("number").orEmpty()
        transport = if (savedInstanceState?.getString("transport") == "trolleybus") {
            Transport.TROLLEYBUS
        } else Transport.BUS
        buildUi()
        updateScreen()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("number", number)
        outState.putString("transport", if (transport == Transport.BUS) "bus" else "trolleybus")
        super.onSaveInstanceState(outState)
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(16))
        }
        scroll.addView(content)

        content.addView(buildHeader())

        // Recent shortcuts sit at the top, away from the keypad: a chip tap opens 2ГИС at once,
        // so it must not be hit by a thumb aiming at digits.
        recentSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(card, dp(22).toFloat())
            setPadding(dp(10), dp(14), dp(10), dp(12))
        }
        content.addView(recentSection, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, dp(14)) })

        val selector = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            background = rounded(track, dp(28).toFloat())
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        busButton = makeSegment("Автобус", R.drawable.ic_bus) {
            transport = Transport.BUS; updateScreen()
        }.apply { announceAs(RadioButton::class.java) { transport == Transport.BUS } }
        trolleyButton = makeSegment("Троллейбус", R.drawable.ic_trolleybus) {
            transport = Transport.TROLLEYBUS; updateScreen()
        }.apply { announceAs(RadioButton::class.java) { transport == Transport.TROLLEYBUS } }
        selector.addView(busButton, LinearLayout.LayoutParams(0, dp(52), 1f))
        selector.addView(trolleyButton, LinearLayout.LayoutParams(0, dp(52), 1f))
        content.addView(selector)

        content.addView(buildNumberField(), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(88)
        ).apply { setMargins(0, dp(16), 0, 0) })
        hintView = text("", 14f, subdued).apply {
            gravity = Gravity.CENTER
            minHeight = dp(36)
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        content.addView(hintView)

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("C", "0", "⌫")
        )
        rows.forEach { keys ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = false
            }
            keys.forEach { key ->
                val button: View = if (key == "⌫") {
                    ImageButton(this).apply {
                        setImageResource(R.drawable.ic_backspace)
                        setColorFilter(ink)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setPadding(0, dp(18), 0, dp(18))
                        contentDescription = "Стереть цифру"
                        elevation = dp(1).toFloat()
                    }
                } else {
                    makeButton(key, 30f).apply {
                        if (key == "C") contentDescription = "Очистить"
                    }
                }
                if (key == "C" || key == "⌫") {
                    button.background = rounded(track, dp(13).toFloat())
                }
                button.setOnClickListener {
                    when (key) {
                        "C" -> number = ""
                        "⌫" -> number = number.dropLast(1)
                        else -> if (number.length < 3) number += key
                    }
                    updateScreen()
                }
                if (key == "⌫") button.setOnLongClickListener {
                    number = ""; updateScreen(); true
                }
                row.addView(button, weighted(58))
            }
            content.addView(row)
        }

        // One line that shrinks with large system fonts instead of wrapping out of the button.
        openLabel = text("", 19f, ink, true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(dp(8), 0, dp(8), 0)
            setAutoSizeTextTypeUniformWithConfiguration(12, 19, 1, TypedValue.COMPLEX_UNIT_SP)
        }
        openButton = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            isClickable = true
            background = rounded(yellow, dp(20).toFloat())
            elevation = dp(1).toFloat()
            setPadding(dp(20), 0, dp(20), 0)
            setOnClickListener { openRoute() }
            addView(icon(R.drawable.ic_place, ink), LinearLayout.LayoutParams(dp(26), dp(26)))
            addView(openLabel, LinearLayout.LayoutParams(0, dp(30), 1f))
            addView(icon(R.drawable.ic_arrow_forward, ink), LinearLayout.LayoutParams(dp(24), dp(24)))
            announceAs(Button::class.java)
        }
        content.addView(openButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(62)
        ).apply { setMargins(dp(4), dp(14), dp(4), 0) })
        content.addView(text("Справочник работает без интернета. Для карты нужен 2ГИС или браузер.",
            12f, subdued).apply {
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(12), dp(6), 0)
        })
        setContentView(scroll)
    }

    private fun updateScreen() {
        numberView.text = number.ifEmpty { "—" }
        clearNumberButton.visibility = if (number.isEmpty()) View.INVISIBLE else View.VISIBLE
        val target = RouteCatalog.resolve(transport, number, entries)
        hintView.text = when {
            target == null -> "Введите номер маршрута"
            target.isDirectRoute -> target.description
            else -> "Прямой ссылки нет — откроется поиск в 2ГИС"
        }
        openButton.isEnabled = target != null
        openButton.alpha = if (target != null) 1f else 0.45f
        openLabel.text = if (target?.isDirectRoute == false) "Найти в 2ГИС" else "Открыть маршрут в 2ГИС"
        openButton.contentDescription = openLabel.text
        setSelected(busButton, transport == Transport.BUS)
        setSelected(trolleyButton, transport == Transport.TROLLEYBUS)
        renderRecentRoutes()
    }

    /** Six one-tap shortcuts in one row, newest first; the chip matching the input is pale yellow. */
    private fun renderRecentRoutes() {
        recentSection.removeAllViews()
        recentSection.visibility = if (recentRoutes.isEmpty()) View.GONE else View.VISIBLE
        if (recentRoutes.isEmpty()) return

        recentSection.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, 0, dp(10))
            addView(icon(R.drawable.ic_clock, subdued), LinearLayout.LayoutParams(dp(18), dp(18)))
            addView(text("Недавние", 14f, subdued, true).apply { setPadding(dp(8), 0, 0, 0) })
        })
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
        }
        // Fixed six slots keep chip width stable while history is still filling up.
        repeat(6) { index ->
            val recent = recentRoutes.getOrNull(index)
            val view = if (recent == null) View(this) else makeRecentChip(recent)
            row.addView(view, LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                setMargins(dp(3), 0, dp(3), 0)
            })
        }
        recentSection.addView(row)
    }

    private fun makeRecentChip(recent: RecentRoute) = LinearLayout(this).apply {
        val active = recent.transport == transport && recent.number == number
        val textColor = if (active) ink else chipText
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        isClickable = true
        background = GradientDrawable().apply {
            setColor(if (active) paleYellow else chip)
            if (!active) setStroke(dp(1), chipStroke)
            cornerRadius = dp(12).toFloat()
        }
        contentDescription = "${recent.transport.caption} ${recent.number}, открыть в 2ГИС"
        setOnClickListener {
            transport = recent.transport
            number = recent.number
            updateScreen()
            openRoute()
        }
        addView(autoSized(recent.number, 20, textColor, true),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(26)))
        // Short label keeps the minimum size readable in six narrow slots; TalkBack reads the full name.
        val label = if (recent.transport == Transport.BUS) "автобус" else "тролл."
        addView(autoSized(label, 11, textColor, false),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(15)))
        announceAs(Button::class.java)
    }

    /**
     * One line that shrinks instead of wrapping in narrow chips. Sizes are in dp, not sp:
     * the chip box is fixed, so system font scaling would only clip the label.
     */
    private fun autoSized(value: String, maxDp: Int, color: Int, bold: Boolean) =
        text(value, maxDp.toFloat(), color, bold).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            includeFontPadding = false
            setPadding(dp(2), 0, dp(2), 0)
            setAutoSizeTextTypeUniformWithConfiguration(9, maxDp, 1, TypedValue.COMPLEX_UNIT_DIP)
        }

    private fun onRouteOpened(openedTransport: Transport, openedNumber: String) {
        recentRoutes = RouteHistory.record(
            recentRoutes, RecentRoute(openedTransport, openedNumber)
        )
        getSharedPreferences("recent_routes", MODE_PRIVATE).edit()
            .putString("items", RouteHistory.encode(recentRoutes))
            .apply()
        number = ""
        updateScreen()
    }

    private fun openRoute() {
        val openedTransport = transport
        val openedNumber = number
        val target = RouteCatalog.resolve(openedTransport, openedNumber, entries) ?: return
        // 2ГИС документирует dgis://2gis.ru/... для Android. Этот переход нужно
        // проверить на реальном телефоне; ниже есть fallback на обычный HTTPS.
        if (target.isDirectRoute) {
            val path = target.url.removePrefix("https://2gis.kz/")
            val nativeIntent = Intent(Intent.ACTION_VIEW,
                Uri.parse("dgis://2gis.ru/$path")).setPackage("ru.dublgis.dgismobile")
            try {
                startActivity(nativeIntent)
                onRouteOpened(openedTransport, openedNumber)
                return
            } catch (_: ActivityNotFoundException) {
                // Нет 2ГИС или текущая версия не принимает такой адрес.
            }
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target.url)))
            onRouteOpened(openedTransport, openedNumber)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Не найдено приложение для открытия ссылки", Toast.LENGTH_LONG).show()
        }
    }

    private fun makeButton(label: String, textSizeSp: Float): Button =
        Button(this).apply {
            text = label
            textSize = textSizeSp
            isAllCaps = false
            gravity = Gravity.CENTER
            includeFontPadding = false
            minHeight = 0
            minWidth = 0
            setPadding(0, 0, 0, 0)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(ink)
            background = rounded(white, dp(13).toFloat())
            elevation = dp(1).toFloat()
        }

    /** Yellow logo tile + title, as in the mockup; no overflow menu (it had no action). */
    private fun buildHeader() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), 0, 0, dp(12))
        addView(icon(R.drawable.ic_bus, ink).apply {
            background = rounded(yellow, dp(14).toFloat())
            setPadding(dp(11), dp(11), dp(11), dp(11))
        }, LinearLayout.LayoutParams(dp(52), dp(52)))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
            addView(text("Быстрый автобус", 24f, ink, true))
            addView(text("Алматы · маршрут в 2ГИС", 14f, subdued))
        })
    }

    /** Segment = icon + label; only the background changes with selection. */
    private fun makeSegment(label: String, iconRes: Int, onClick: () -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            isClickable = true
            contentDescription = label
            setOnClickListener { onClick() }
            addView(icon(iconRes, ink), LinearLayout.LayoutParams(dp(24), dp(24)))
            addView(text(label, 16f, ink, true).apply { setPadding(dp(8), 0, 0, 0) })
        }

    private fun setSelected(segment: LinearLayout, selected: Boolean) {
        segment.isSelected = selected
        segment.background = if (selected) rounded(yellow, dp(24).toFloat()) else null
    }

    /** White field with yellow outline; ✕ clears the number (hidden when empty). */
    private fun buildNumberField() = FrameLayout(this).apply {
        background = GradientDrawable().apply {
            setColor(white)
            setStroke(dp(2), yellow)
            cornerRadius = dp(22).toFloat()
        }
        numberView = text("—", 64f, ink, true).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        addView(numberView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        // 48dp touch target; the grey tile is inset so it still looks 40dp.
        clearNumberButton = icon(R.drawable.ic_close, ink).apply {
            background = InsetDrawable(rounded(track, dp(10).toFloat()), dp(4))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            contentDescription = "Очистить номер"
            setOnClickListener { number = ""; updateScreen() }
            announceAs(Button::class.java)
        }
        addView(clearNumberButton, FrameLayout.LayoutParams(dp(48), dp(48),
            Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = dp(14) })
    }

    private fun icon(iconRes: Int, tint: Int) = ImageView(this).apply {
        setImageResource(iconRes)
        setColorFilter(tint)
    }

    /**
     * Composite views (LinearLayout/ImageView) lost the Button role of the old widgets.
     * Report [role] to accessibility services, plus checked state for radio-like segments,
     * and keep inner icons/labels out of the tree so the item is announced once.
     */
    private fun View.announceAs(role: Class<*>, checked: (() -> Boolean)? = null) {
        if (this is ViewGroup) {
            for (i in 0 until childCount) {
                getChildAt(i).importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        }
        accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = role.name
                if (checked != null) {
                    info.isCheckable = true
                    info.isChecked = checked()
                }
            }
        }
    }

    private fun text(value: String, sizeSp: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = sizeSp
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun weighted(heightDp: Int) = LinearLayout.LayoutParams(0, dp(heightDp), 1f).apply {
        setMargins(dp(4), dp(3), dp(4), dp(3))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}
