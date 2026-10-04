package dev.fuelwallet.lukoiltest;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.*;
import android.widget.*;

/** Official station accounts with persistent, origin-scoped website storage. */
public final class MainActivity extends Activity {
    private static final String[] STATIONS = {"Лукойл", "Teboil", "Газпромнефть", "Татнефть"};
    private static final String[] HOME_URLS = {"https://azs.lukoil.ru/main", "https://lk.teboil.ru/", "https://gpnbonus.ru/pwa/", "https://azs.tatneft.ru/login"};
    private static final String[][] HOSTS = {{"azs.lukoil.ru"}, {"lk.teboil.ru"}, {"gpnbonus.ru", "www.gpnbonus.ru"}, {"azs.tatneft.ru"}};
    private static final int INK = Color.rgb(245, 247, 247), MUTED = Color.rgb(171, 181, 180);
    private static final int PAPER = Color.rgb(16, 21, 24), GREEN = Color.rgb(183, 237, 212);
    private int station;
    private boolean clearHistory, pageError, expanded, foreground, walletVisible;
    private WebView web;
    private TextView status, stationTitle;
    private ProgressBar progress;
    private Button backButton, menuButton, scanButton;
    private LinearLayout page;
    private ScrollView wallet;

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private GradientDrawable background(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private Button button(String label, boolean primary, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(14);
        b.setTextColor(primary ? PAPER : INK); b.setMinWidth(0); b.setMinimumWidth(0);
        b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48));
        if (primary) {
            b.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.rgb(135, 196, 167)), background(GREEN, 12), null));
        } else b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PAPER));
        b.setOnClickListener(v -> action.run()); return b;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        station = state != null ? state.getInt("station", 0) : getPreferences(MODE_PRIVATE).getInt("station", 0);
        if (station < 0 || station >= STATIONS.length) station = 0;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(PAPER); getWindow().setNavigationBarColor(PAPER);
        getWindow().getDecorView().setSystemUiVisibility(0);
        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(PAPER);
        page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setBackgroundColor(PAPER);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout toolbar = new LinearLayout(this); toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8), 0, dp(8), 0);
        page.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(56)));
        backButton = button("Все карты", false, () -> { if (expanded) setExpanded(false); else showWallet(); });
        toolbar.addView(backButton, new LinearLayout.LayoutParams(dp(100), -1));
        stationTitle = text(STATIONS[station], 18, INK, true);
        stationTitle.setGravity(Gravity.CENTER_VERTICAL); stationTitle.setPadding(dp(8), 0, dp(4), 0);
        stationTitle.setMaxLines(1); stationTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        toolbar.addView(stationTitle, new LinearLayout.LayoutParams(0, -1, 1));
        menuButton = button("Меню", false, () -> showMenu());
        toolbar.addView(menuButton, new LinearLayout.LayoutParams(dp(72), -1));
        status = text("", 12, MUTED, false); status.setPadding(dp(16), 0, dp(16), dp(6));
        status.setMaxLines(2); page.addView(status);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));
        page.addView(progress, new LinearLayout.LayoutParams(-1, dp(2)));
        web = new WebView(this); page.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout footer = new LinearLayout(this); footer.setPadding(dp(16), dp(8), dp(16), dp(8));
        scanButton = button("Показать на кассе", true, () -> setExpanded(true));
        footer.addView(scanButton, new LinearLayout.LayoutParams(-1, dp(48)));
        page.addView(footer);
        scanButton.setTag(footer);

        wallet = new ScrollView(this); wallet.setFillViewport(true); wallet.setBackgroundColor(PAPER);
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(20), dp(28), dp(20), dp(24)); wallet.addView(list);
        LinearLayout brand = new LinearLayout(this); brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView appLogo = new ImageView(this); appLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        appLogo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        try (java.io.InputStream input = getAssets().open("karman.png")) { appLogo.setImageBitmap(BitmapFactory.decodeStream(input)); }
        catch (java.io.IOException ignored) { }
        brand.addView(appLogo, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView heading = text("Карман", 28, INK, true); heading.setPadding(dp(12), 0, 0, 0); brand.addView(heading);
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(-1, -2); brandParams.bottomMargin = dp(24);
        list.addView(brand, brandParams);
        String[] logos = {"lukoil.png", "teboil.png", "gpn.webp", "tatneft.png"};
        int[] tones = {Color.rgb(64, 28, 37), Color.rgb(22, 43, 75), Color.rgb(15, 48, 61), Color.rgb(27, 51, 37)};
        for (int i = 0; i < STATIONS.length; i++) {
            final int index = i;
            LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(20), dp(16), dp(20), dp(16)); card.setMinimumHeight(dp(128));
            card.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.rgb(85, 99, 106)), background(tones[i], 16), null));
            card.setClipToOutline(true); card.setFocusable(true); card.setClickable(true);
            card.setContentDescription(STATIONS[i] + ", открыть карту"); card.setOnClickListener(v -> selectStation(index));
            LinearLayout identity = new LinearLayout(this); identity.setGravity(Gravity.CENTER_VERTICAL);
            ImageView logo = new ImageView(this); logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            try (java.io.InputStream input = getAssets().open(logos[i])) {
                android.graphics.Bitmap mark = BitmapFactory.decodeStream(input); logo.setImageBitmap(mark);
                if (i == 2) {
                    // Display the official adaptive foreground without its launcher safe-zone padding.
                    logo.setScaleType(ImageView.ScaleType.MATRIX);
                    float scale = dp(56) / 228f;
                    android.graphics.Matrix matrix = new android.graphics.Matrix();
                    matrix.setScale(scale, scale); matrix.postTranslate(-102f * scale, -102f * scale);
                    logo.setImageMatrix(matrix);
                }
            }
            catch (java.io.IOException ignored) { /* The readable station name remains the fallback. */ }
            logo.setBackground(background(Color.WHITE, 12)); logo.setClipToOutline(true);
            identity.addView(logo, new LinearLayout.LayoutParams(dp(56), dp(56)));
            LinearLayout names = new LinearLayout(this); names.setOrientation(LinearLayout.VERTICAL); names.setPadding(dp(16), 0, 0, 0);
            names.addView(text(STATIONS[i], 20, INK, true));
            TextView detail = text("Карта лояльности", 14, Color.rgb(213, 223, 230), false); detail.setPadding(0, dp(4), 0, 0);
            names.addView(detail); identity.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
            card.addView(identity);
            LinearLayout openRow = new LinearLayout(this); openRow.setGravity(Gravity.CENTER_VERTICAL); openRow.setPadding(0, dp(16), 0, 0);
            openRow.addView(text("Открыть карту", 14, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
            ImageView arrow = new ImageView(this); arrow.setImageDrawable(new ArrowDrawable());
            arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            openRow.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(24))); card.addView(openRow);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2); cardParams.bottomMargin = dp(12);
            list.addView(card, cardParams);
        }
        root.addView(wallet, new FrameLayout.LayoutParams(-1, -1)); setContentView(root);

        WebSettings settings = web.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW); settings.setSafeBrowsingEnabled(true);
        settings.setSupportMultipleWindows(false);
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri uri = req.getUrl(); if (isStationUrl(uri.toString())) return false;
                if ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (android.content.ActivityNotFoundException e) { status.setText("Не удалось открыть ссылку: установите браузер"); }
                } else status.setText("Эта ссылка не поддерживается");
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                pageError = false; progress.setVisibility(View.VISIBLE);
                status.setText("Загрузка страницы АЗС…"); updateScanMode(false);
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (!isStationUrl(url)) return;
                progress.setVisibility(View.GONE);
                if (!pageError) status.setText("Официальный сайт · " + Uri.parse(url).getHost());
                CookieManager.getInstance().flush();
                if (clearHistory) { web.clearHistory(); clearHistory = false; }
                if (isMain(url)) focusQr();
                updateScanMode(!pageError && expanded && !walletVisible);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError error) {
                if (req.isForMainFrame()) showError("Нет соединения. Проверьте интернет и выберите «Обновить» в меню.");
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest req, WebResourceResponse response) {
                if (req.isForMainFrame()) showError("Сайт АЗС недоступен (" + response.getStatusCode() + "). Выберите «Обновить» в меню.");
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) { progress.setProgress(value); }
        });
        boolean restored = state != null && web.restoreState(state) != null;
        if (restored && !state.getBoolean("walletVisible", true)) {
            walletVisible = false; wallet.setVisibility(View.GONE); page.setVisibility(View.VISIBLE);
            setExpanded(state.getBoolean("expanded", false));
        } else showWallet();

    }

    private final class ArrowDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas canvas) {
            paint.setColor(INK); paint.setStrokeWidth(dp(2)); paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
            float x = getBounds().left, y = getBounds().top, w = getBounds().width(), h = getBounds().height();
            Path path = new Path(); path.moveTo(x+w*.2f, y+h*.5f); path.lineTo(x+w*.8f, y+h*.5f);
            path.moveTo(x+w*.55f, y+h*.25f); path.lineTo(x+w*.8f, y+h*.5f); path.lineTo(x+w*.55f, y+h*.75f);
            canvas.drawPath(path, paint);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private void showError(String message) {
        pageError = true; status.setText(message); progress.setVisibility(View.GONE);
        setExpanded(false); updateScanMode(false);
    }
    private void showMenu() {
        PopupMenu menu = new PopupMenu(this, menuButton);
        menu.getMenu().add("Обновить").setOnMenuItemClickListener(item -> { web.reload(); return true; });
        menu.getMenu().add("Личный кабинет").setOnMenuItemClickListener(item -> { setExpanded(false); web.loadUrl(HOME_URLS[station]); return true; });
        menu.getMenu().add("Все карты").setOnMenuItemClickListener(item -> { showWallet(); return true; });
        menu.show();
    }
    private void setExpanded(boolean value) {
        expanded = value;
        backButton.setText(value ? "Готово" : "Все карты");
        stationTitle.setText(value ? "На кассе" : STATIONS[station]);
        menuButton.setVisibility(value ? View.GONE : View.VISIBLE);
        status.setVisibility(value ? View.GONE : View.VISIBLE);
        ((View) scanButton.getTag()).setVisibility(value ? View.GONE : View.VISIBLE);
        updateScanMode(value && !pageError && !walletVisible && isStationUrl(web.getUrl()));
    }
    private void showWallet() {
        walletVisible = true; setExpanded(false); CookieManager.getInstance().flush();
        wallet.setVisibility(View.VISIBLE); page.setVisibility(View.GONE);
    }
    private void selectStation(int next) {
        boolean reuse = station == next && isStationUrl(web.getUrl());
        CookieManager.getInstance().flush();
        if (!reuse) { web.stopLoading(); station = next; clearHistory = true; pageError = false; }
        walletVisible = false; wallet.setVisibility(View.GONE); page.setVisibility(View.VISIBLE); setExpanded(false);
        getPreferences(MODE_PRIVATE).edit().putInt("station", station).apply();
        if (!reuse) web.loadUrl(HOME_URLS[station]);
    }
    private boolean isStationUrl(String url) {
        if (url == null) return false; Uri uri = Uri.parse(url); if (!"https".equals(uri.getScheme())) return false;
        for (String host : HOSTS[station]) if (host.equals(uri.getHost())) return true; return false;
    }
    private boolean isMain(String url) {
        return url != null && "https".equals(Uri.parse(url).getScheme()) && "azs.lukoil.ru".equals(Uri.parse(url).getHost()) && "/main".equals(Uri.parse(url).getPath());
    }
    private void focusQr() {
        if (!isMain(web.getUrl())) return;
        web.evaluateJavascript("(()=>{if(window.__fuelQrObserver)window.__fuelQrObserver.disconnect();"
            + "const scroll=()=>{const e=document.querySelector('[class*=qr_container_]');"
            + "if(!e||!e.querySelector('svg,canvas'))return false;"
            + "e.scrollIntoView({block:'center',behavior:'instant'});return true;};"
            + "if(scroll())return;const o=new MutationObserver(()=>{if(scroll())o.disconnect();});"
            + "window.__fuelQrObserver=o;o.observe(document.body,{childList:true,subtree:true});"
            + "setTimeout(()=>o.disconnect(),10000);})()", null);
    }
    private void updateScanMode(boolean scanning) {
        boolean enabled = scanning && foreground;
        if (enabled) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams attrs = getWindow().getAttributes();
        attrs.screenBrightness = enabled ? 0.9f : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(attrs);
    }
    @Override protected void onResume() {
        super.onResume(); foreground = true;
        if (web != null) { web.onResume(); updateScanMode(expanded && !walletVisible && !pageError && isStationUrl(web.getUrl())); }
    }
    @Override protected void onPause() {
        foreground = false; updateScanMode(false); CookieManager.getInstance().flush();
        if (web != null) web.onPause(); super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("station", station); state.putBoolean("walletVisible", walletVisible); state.putBoolean("expanded", expanded);
        web.saveState(state); super.onSaveInstanceState(state);
    }
    @Override public void onBackPressed() {
        if (walletVisible) super.onBackPressed();
        else if (expanded) setExpanded(false);
        else if (web.canGoBack()) web.goBack();
        else showWallet();
    }
    @Override protected void onDestroy() { web.destroy(); super.onDestroy(); }
}
