package dev.fuelwallet.lukoiltest

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.io.IOException
import kotlin.math.roundToInt

/** Official station accounts with persistent, origin-scoped website storage. */
class MainActivity : Activity() {
    private data class Station(
        val name: String,
        val homeUrl: String,
        val hosts: Set<String>,
        val logo: String,
        val tone: Int,
    )

    private companion object {
        val INK = Color.rgb(245, 247, 247)
        val MUTED = Color.rgb(171, 181, 180)
        val PAPER = Color.rgb(16, 21, 24)
        val GREEN = Color.rgb(183, 237, 212)
        // Keep this order: existing installations persist the selected station's index.
        val STATIONS = listOf(
            Station("Лукойл", "https://azs.lukoil.ru/main", setOf("azs.lukoil.ru"), "lukoil.png", Color.rgb(64, 28, 37)),
            Station("Teboil", "https://lk.teboil.ru/", setOf("lk.teboil.ru"), "teboil.png", Color.rgb(22, 43, 75)),
            Station("Газпромнефть", "https://gpnbonus.ru/pwa/", setOf("gpnbonus.ru", "www.gpnbonus.ru"), "gpn.webp", Color.rgb(15, 48, 61)),
            Station("Татнефть", "https://azs.tatneft.ru/login", setOf("azs.tatneft.ru"), "tatneft.png", Color.rgb(27, 51, 37)),
        )
    }

    private var station = 0
    private var clearHistory = false
    private var pageError = false
    private var expanded = false
    private var foreground = false
    private var walletVisible = false
    private lateinit var web: WebView
    private lateinit var status: TextView
    private lateinit var stationTitle: TextView
    private lateinit var progress: ProgressBar
    private lateinit var backButton: Button
    private lateinit var menuButton: Button
    private lateinit var footer: LinearLayout
    private lateinit var page: LinearLayout
    private lateinit var wallet: ScrollView
    private val currentStation get() = STATIONS[station]

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private fun text(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun background(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun button(label: String, primary: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setTextColor(if (primary) PAPER else INK)
        minWidth = 0
        minimumWidth = 0
        minHeight = dp(48)
        minimumHeight = dp(48)
        if (primary) {
            background = RippleDrawable(ColorStateList.valueOf(Color.rgb(135, 196, 167)), background(GREEN, 12), null)
        } else {
            backgroundTintList = ColorStateList.valueOf(PAPER)
        }
        setOnClickListener { action() }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        station = state?.getInt("station", 0) ?: getPreferences(MODE_PRIVATE).getInt("station", 0)
        if (station !in STATIONS.indices) station = 0
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.statusBarColor = PAPER
        window.navigationBarColor = PAPER
        window.decorView.systemUiVisibility = 0

        val root = FrameLayout(this).apply { setBackgroundColor(PAPER) }
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(PAPER)
        }
        root.addView(page, FrameLayout.LayoutParams(-1, -1))
        createToolbar()
        status = text("", 12, MUTED).apply {
            setPadding(dp(16), 0, dp(16), dp(6))
            maxLines = 2
        }
        page.addView(status)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            progressTintList = ColorStateList.valueOf(GREEN)
        }
        page.addView(progress, LinearLayout.LayoutParams(-1, dp(2)))
        web = WebView(this)
        page.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        footer = LinearLayout(this).apply {
            setPadding(dp(16), dp(8), dp(16), dp(8))
            addView(button("Показать на кассе", primary = true) { setExpanded(true) }, LinearLayout.LayoutParams(-1, dp(48)))
        }
        page.addView(footer)
        wallet = createWallet()
        root.addView(wallet, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        configureWebView()

        if (state != null && web.restoreState(state) != null && !state.getBoolean("walletVisible", true)) {
            walletVisible = false
            wallet.visibility = View.GONE
            page.visibility = View.VISIBLE
            setExpanded(state.getBoolean("expanded", false))
        } else {
            showWallet()
        }
    }

    private fun createToolbar() {
        val toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        page.addView(toolbar, LinearLayout.LayoutParams(-1, dp(56)))
        backButton = button("Все карты") { if (expanded) setExpanded(false) else showWallet() }
        toolbar.addView(backButton, LinearLayout.LayoutParams(dp(100), -1))
        stationTitle = text(currentStation.name, 18, INK, bold = true).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(4), 0)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        toolbar.addView(stationTitle, LinearLayout.LayoutParams(0, -1, 1f))
        menuButton = button("Меню") { showMenu() }
        toolbar.addView(menuButton, LinearLayout.LayoutParams(dp(72), -1))
    }

    private fun logo(asset: String) = ImageView(this).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        try {
            assets.open(asset).use { setImageBitmap(BitmapFactory.decodeStream(it)) }
        } catch (_: IOException) {
            // The readable station name remains the fallback.
        }
    }

    private fun createWallet(): ScrollView {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(24))
        }
        val brand = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(logo("karman.png"), LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(text("Карман", 28, INK, bold = true).apply { setPadding(dp(12), 0, 0, 0) })
        }
        list.addView(brand, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(24) })
        STATIONS.forEachIndexed { index, destination ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(16), dp(20), dp(16))
                minimumHeight = dp(128)
                background = RippleDrawable(ColorStateList.valueOf(Color.rgb(85, 99, 106)), background(destination.tone, 16), null)
                clipToOutline = true
                isFocusable = true
                isClickable = true
                contentDescription = "${destination.name}, открыть карту"
                setOnClickListener { selectStation(index) }
            }
            val identity = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val mark = logo(destination.logo).apply {
                if (destination.logo == "gpn.webp") {
                    // Display the official adaptive foreground without its launcher safe-zone padding.
                    scaleType = ImageView.ScaleType.MATRIX
                    val scale = dp(56) / 228f
                    imageMatrix = Matrix().apply {
                        setScale(scale, scale)
                        postTranslate(-102f * scale, -102f * scale)
                    }
                }
                background = background(Color.WHITE, 12)
                clipToOutline = true
            }
            identity.addView(mark, LinearLayout.LayoutParams(dp(56), dp(56)))
            val names = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), 0, 0, 0)
                addView(text(destination.name, 20, INK, bold = true))
                addView(text("Карта лояльности", 14, Color.rgb(213, 223, 230)).apply { setPadding(0, dp(4), 0, 0) })
            }
            identity.addView(names, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(identity)
            val openRow = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(16), 0, 0)
                addView(text("Открыть карту", 14, INK, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
                addView(ImageView(this@MainActivity).apply {
                    setImageDrawable(ArrowDrawable())
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(dp(24), dp(24)))
            }
            card.addView(openRow)
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        }
        return ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(PAPER)
            addView(list)
        }
    }

    private fun configureWebView() {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, false)
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (isStationUrl(uri.toString())) return false
                if (uri.scheme == "https" || uri.scheme == "http") {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (_: ActivityNotFoundException) {
                        status.text = "Не удалось открыть ссылку: установите браузер"
                    }
                } else {
                    status.text = "Эта ссылка не поддерживается"
                }
                return true
            }
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                pageError = false
                progress.visibility = View.VISIBLE
                status.text = "Загрузка страницы АЗС…"
                updateScanMode(false)
            }
            override fun onPageFinished(view: WebView, url: String) {
                if (!isStationUrl(url)) return
                progress.visibility = View.GONE
                if (!pageError) status.text = "Официальный сайт · ${Uri.parse(url).host}"
                CookieManager.getInstance().flush()
                if (clearHistory) {
                    web.clearHistory()
                    clearHistory = false
                }
                if (isMain(url)) focusQr()
                updateScanMode(!pageError && expanded && !walletVisible)
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) showError("Нет соединения. Проверьте интернет и выберите «Обновить» в меню.")
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) showError("Сайт АЗС недоступен (${response.statusCode}). Выберите «Обновить» в меню.")
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { progress.progress = newProgress }
        }
    }

    private inner class ArrowDrawable : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun draw(canvas: Canvas) {
            paint.apply {
                color = INK
                strokeWidth = dp(2).toFloat()
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val x = bounds.left.toFloat()
            val y = bounds.top.toFloat()
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            val path = Path().apply {
                moveTo(x + w * .2f, y + h * .5f)
                lineTo(x + w * .8f, y + h * .5f)
                moveTo(x + w * .55f, y + h * .25f)
                lineTo(x + w * .8f, y + h * .5f)
                lineTo(x + w * .55f, y + h * .75f)
            }
            canvas.drawPath(path, paint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Android")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    private fun showError(message: String) {
        pageError = true
        status.text = message
        progress.visibility = View.GONE
        setExpanded(false)
        updateScanMode(false)
    }

    private fun showMenu() {
        PopupMenu(this, menuButton).apply {
            menu.add("Обновить").setOnMenuItemClickListener { web.reload(); true }
            menu.add("Личный кабинет").setOnMenuItemClickListener { setExpanded(false); web.loadUrl(currentStation.homeUrl); true }
            menu.add("Все карты").setOnMenuItemClickListener { showWallet(); true }
            show()
        }
    }

    private fun setExpanded(value: Boolean) {
        expanded = value
        backButton.text = if (value) "Готово" else "Все карты"
        stationTitle.text = if (value) "На кассе" else currentStation.name
        val visibility = if (value) View.GONE else View.VISIBLE
        menuButton.visibility = visibility
        status.visibility = visibility
        footer.visibility = visibility
        updateScanMode(value && !pageError && !walletVisible && isStationUrl(web.url))
    }

    private fun showWallet() {
        walletVisible = true
        setExpanded(false)
        CookieManager.getInstance().flush()
        wallet.visibility = View.VISIBLE
        page.visibility = View.GONE
    }

    private fun selectStation(next: Int) {
        val reuse = station == next && isStationUrl(web.url)
        CookieManager.getInstance().flush()
        if (!reuse) {
            web.stopLoading()
            station = next
            clearHistory = true
            pageError = false
        }
        walletVisible = false
        wallet.visibility = View.GONE
        page.visibility = View.VISIBLE
        setExpanded(false)
        getPreferences(MODE_PRIVATE).edit().putInt("station", station).apply()
        if (!reuse) web.loadUrl(currentStation.homeUrl)
    }

    private fun isStationUrl(url: String?): Boolean {
        if (url == null) return false
        val uri = Uri.parse(url)
        return uri.scheme == "https" && uri.host in currentStation.hosts
    }

    private fun isMain(url: String?): Boolean {
        if (url == null) return false
        val uri = Uri.parse(url)
        return uri.scheme == "https" && uri.host == "azs.lukoil.ru" && uri.path == "/main"
    }

    private fun focusQr() {
        if (!isMain(web.url)) return
        // Scroll the official QR component into view without reading its contents.
        web.evaluateJavascript(
            """
            (()=>{if(window.__fuelQrObserver)window.__fuelQrObserver.disconnect();
            const scroll=()=>{const e=document.querySelector('[class*=qr_container_]');
            if(!e||!e.querySelector('svg,canvas'))return false;
            e.scrollIntoView({block:'center',behavior:'instant'});return true;};
            if(scroll())return;const o=new MutationObserver(()=>{if(scroll())o.disconnect();});
            window.__fuelQrObserver=o;o.observe(document.body,{childList:true,subtree:true});
            setTimeout(()=>o.disconnect(),10000);})()
            """.trimIndent(), null,
        )
    }

    private fun updateScanMode(scanning: Boolean) {
        val enabled = scanning && foreground
        if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            screenBrightness = if (enabled) .9f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        if (::web.isInitialized) {
            web.onResume()
            updateScanMode(expanded && !walletVisible && !pageError && isStationUrl(web.url))
        }
    }
    override fun onPause() {
        foreground = false
        updateScanMode(false)
        CookieManager.getInstance().flush()
        if (::web.isInitialized) web.onPause()
        super.onPause()
    }
    override fun onSaveInstanceState(state: Bundle) {
        state.putInt("station", station)
        state.putBoolean("walletVisible", walletVisible)
        state.putBoolean("expanded", expanded)
        web.saveState(state)
        super.onSaveInstanceState(state)
    }
    @Deprecated("Uses the existing Activity back navigation")
    override fun onBackPressed() {
        when {
            walletVisible -> super.onBackPressed()
            expanded -> setExpanded(false)
            web.canGoBack() -> web.goBack()
            else -> showWallet()
        }
    }
    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
