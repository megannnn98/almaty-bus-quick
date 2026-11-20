package kz.example.busroutequick

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** MVP: полностью локальный справочник и стандартный Android ACTION_VIEW. */
class MainActivity : Activity() {
    private val bg = Color.rgb(247, 248, 251)
    private val ink = Color.rgb(25, 32, 45)
    private val subdued = Color.rgb(102, 115, 138)
    private val blue = Color.rgb(36, 87, 245)
    private val white = Color.WHITE

    private var number = ""
    private var transport = Transport.BUS
    private lateinit var entries: List<RouteEntry>
    private lateinit var numberView: TextView
    private lateinit var hintView: TextView
    private lateinit var openButton: Button
    private lateinit var busButton: Button
    private lateinit var trolleyButton: Button
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
            setPadding(dp(18), dp(18), dp(18), dp(20))
        }
        scroll.addView(content)

        content.addView(text("Быстрый автобус", 25f, ink, true).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        content.addView(text("Алматы · сразу в 2ГИС", 14f, subdued).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(5), 0, dp(18))
        })

        val selector = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        busButton = makeButton("Автобус", 16f, false).apply {
            setOnClickListener { transport = Transport.BUS; updateScreen() }
        }
        trolleyButton = makeButton("Троллейбус", 16f, false).apply {
            setOnClickListener { transport = Transport.TROLLEYBUS; updateScreen() }
        }
        selector.addView(busButton, weighted(54))
        selector.addView(trolleyButton, weighted(54))
        content.addView(selector)

        numberView = text("—", 64f, ink, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(3))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        content.addView(numberView)
        hintView = text("", 15f, subdued).apply {
            gravity = Gravity.CENTER
            minHeight = dp(54)
            setPadding(dp(8), 0, dp(8), dp(8))
        }
        content.addView(hintView)

        recentSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(recentSection)

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("Очистить", "0", "⌫")
        )
        rows.forEach { keys ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            keys.forEach { key ->
                val button = makeButton(key, if (key == "Очистить") 14f else 27f, false)
                button.setOnClickListener {
                    when (key) {
                        "Очистить" -> number = ""
                        "⌫" -> number = number.dropLast(1)
                        else -> if (number.length < 3) number += key
                    }
                    updateScreen()
                }
                if (key == "⌫") button.setOnLongClickListener {
                    number = ""; updateScreen(); true
                }
                row.addView(button, weighted(65))
            }
            content.addView(row)
        }

        openButton = makeButton("Открыть в 2ГИС", 19f, true).apply {
            setOnClickListener { openRoute() }
        }
        content.addView(openButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(66)
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
        val target = RouteCatalog.resolve(transport, number, entries)
        hintView.text = when {
            target == null -> "Введите номер маршрута"
            target.isDirectRoute -> target.description
            else -> "Прямой ссылки нет — откроется поиск в 2ГИС"
        }
        openButton.isEnabled = target != null
        openButton.alpha = if (target != null) 1f else 0.45f
        openButton.text = if (target?.isDirectRoute == false) "Найти в 2ГИС" else "Открыть маршрут в 2ГИС"
        setSelected(busButton, transport == Transport.BUS)
        setSelected(trolleyButton, transport == Transport.TROLLEYBUS)
        renderRecentRoutes()
    }

    /** Six one-tap shortcuts, newest first. */
    private fun renderRecentRoutes() {
        recentSection.removeAllViews()
        recentSection.visibility = if (recentRoutes.isEmpty()) View.GONE else View.VISIBLE
        if (recentRoutes.isEmpty()) return

        recentSection.addView(text("Последние маршруты", 14f, subdued, true).apply {
            setPadding(dp(6), dp(4), 0, dp(4))
        })
        recentRoutes.chunked(3).forEach { chunk ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            repeat(3) { index ->
                val recent = chunk.getOrNull(index)
                val view: View = if (recent == null) {
                    View(this)
                } else {
                    makeButton(
                        if (recent.transport == Transport.BUS) "Авт. ${recent.number}"
                        else "Тр. ${recent.number}",
                        16f,
                        false
                    ).apply {
                        contentDescription =
                            "${recent.transport.caption} ${recent.number}, открыть в 2ГИС"
                        setOnClickListener {
                            transport = recent.transport
                            number = recent.number
                            updateScreen()
                            openRoute()
                        }
                    }
                }
                row.addView(view, weighted(46))
            }
            recentSection.addView(row)
        }
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

    private fun makeButton(label: String, textSizeSp: Float, primary: Boolean): Button =
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
            setTextColor(if (primary) white else ink)
            background = rounded(if (primary) blue else white, dp(13).toFloat())
            elevation = dp(1).toFloat()
        }

    private fun setSelected(button: Button, selected: Boolean) {
        button.background = rounded(if (selected) blue else white, dp(13).toFloat())
        button.setTextColor(if (selected) white else ink)
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
        setMargins(dp(4), dp(4), dp(4), dp(4))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}
