package dev.salontv;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.util.TypedValue;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.*;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * General-purpose web browser for a D-pad remote. The arrows move an on-screen pointer, OK taps
 * what is under it, and pushing against an edge scrolls. Contains no site-specific code.
 */
public final class BrowserActivity extends Activity {
    private static final String SEARCH = "https://duckduckgo.com/?q=";
    private static final String BLANK = "about:blank";
    private static final int BAR = 84;

    private float scale = 1;
    private FrameLayout content, fullscreen;
    private LinearLayout column;
    private WebView web;
    private EditText address;
    private TextView star;
    private View progress;
    private Pointer pointer;
    private SharedPreferences prefs;
    private float cx, cy;
    private long pressedAt;
    private boolean onStart, clearHistory;
    private String title = "";
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private final Runnable hidePointer = () -> { if (pointer != null) pointer.animate().alpha(0).setDuration(300).start(); };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        Point display = new Point(); getWindowManager().getDefaultDisplay().getRealSize(display);
        scale = Math.min(display.x / 1920f, display.y / 1080f);
        cx = display.x / 2f; cy = display.y / 2f;
        prefs = getSharedPreferences("browser", Context.MODE_PRIVATE);
        try { web = new WebView(this); }
        catch (Exception e) { Toast.makeText(this, "Le moteur web du Player est indisponible.", Toast.LENGTH_LONG).show(); finish(); return; }
        build();
        configure();
        if (state == null || web.restoreState(state) == null) showStart();
    }

    private int px(float n) { return Math.round(n * scale); }

    private void build() {
        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(0xff0d0c12);
        content = new FrameLayout(this);
        column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL);

        LinearLayout bar = new LinearLayout(this); bar.setOrientation(LinearLayout.HORIZONTAL); bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xff17151f); bar.setPadding(px(48), 0, px(48), 0);
        button(bar, "‹", 80, () -> back(false));
        button(bar, "›", 80, () -> { if (web.canGoForward()) web.goForward(); });
        button(bar, "↻", 80, () -> { if (onStart) showStart(); else web.reload(); });
        button(bar, "Accueil", 170, this::showStart);
        address = new EditText(this);
        address.setSingleLine(true); address.setTextColor(Color.WHITE); address.setHintTextColor(0xff9a96a6);
        address.setHint("Adresse ou recherche");
        address.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(27));
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        address.setSelectAllOnFocus(true); address.setPadding(px(26), 0, px(26), 0);
        address.setBackground(rounded(0xff2a2735, 0xff4a4658));
        address.setOnEditorActionListener((v, action, event) -> { open(address.getText().toString()); return true; });
        address.setOnClickListener(v -> keyboard(true));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, px(58), 1); ap.leftMargin = px(14); ap.rightMargin = px(14);
        bar.addView(address, ap);
        star = button(bar, "☆ Favori", 190, this::toggleFavorite);
        column.addView(bar, new LinearLayout.LayoutParams(-1, px(BAR)));

        FrameLayout track = new FrameLayout(this); track.setBackgroundColor(0xff17151f);
        progress = new View(this); progress.setBackgroundColor(0xffe3e9f2); progress.setPivotX(0); progress.setScaleX(0);
        track.addView(progress, new FrameLayout.LayoutParams(-1, -1));
        column.addView(track, new LinearLayout.LayoutParams(-1, px(4)));
        column.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));

        fullscreen = new FrameLayout(this); fullscreen.setBackgroundColor(Color.BLACK); fullscreen.setVisibility(View.GONE);
        content.addView(column, new FrameLayout.LayoutParams(-1, -1));
        content.addView(fullscreen, new FrameLayout.LayoutParams(-1, -1));
        pointer = new Pointer();
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        root.addView(pointer, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    private GradientDrawable rounded(int fill, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(px(29)); d.setStroke(px(1.5f), stroke); return d;
    }

    private TextView button(LinearLayout bar, String label, float width, Runnable action) {
        TextView t = new TextView(this); t.setText(label); t.setTextColor(Color.WHITE); t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(label.length() == 1 ? 44 : 25));
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); t.setIncludeFontPadding(false);
        t.setBackground(rounded(0x00000000, 0xff3a3747)); t.setClickable(true); t.setFocusable(false);
        t.setOnClickListener(v -> { keyboard(false); action.run(); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(width), px(58)); lp.leftMargin = px(5); lp.rightMargin = px(5);
        bar.addView(t, lp); return t;
    }

    private void configure() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true); s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true); s.setBuiltInZoomControls(true); s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setSupportMultipleWindows(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setBackgroundColor(0xff0d0c12); web.setFocusable(true); web.setFocusableInTouchMode(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                return !("http".equals(scheme) || "https".equals(scheme)); // No hand-off to other apps.
            }
            @Override public void onPageStarted(WebView view, String url, Bitmap icon) {
                if (!BLANK.equals(url)) onStart = false;
                progress.setScaleX(.08f); showAddress(url);
            }
            @Override public void onPageFinished(WebView view, String url) {
                progress.setScaleX(0);
                if (clearHistory) { clearHistory = false; view.clearHistory(); }
                if (!onStart && url != null && url.startsWith("http")) prefs.edit().putString("last", url).putString("last_title", title).apply();
                showAddress(url);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) { progress.setScaleX(value >= 100 ? 0 : Math.max(.08f, value / 100f)); }
            @Override public void onReceivedTitle(WebView view, String value) { title = value == null ? "" : value; }
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) { callback.onCustomViewHidden(); return; }
                customView = view; customCallback = callback;
                fullscreen.addView(view, new FrameLayout.LayoutParams(-1, -1));
                fullscreen.setVisibility(View.VISIBLE); column.setVisibility(View.INVISIBLE);
            }
            @Override public void onHideCustomView() { leaveFullscreen(); }
        });
    }

    private void leaveFullscreen() {
        if (customView == null) return;
        fullscreen.removeView(customView); fullscreen.setVisibility(View.GONE); column.setVisibility(View.VISIBLE);
        customView = null;
        if (customCallback != null) customCallback.onCustomViewHidden();
        customCallback = null;
    }

    private void showAddress(String url) {
        if (!address.hasFocus()) address.setText(onStart || url == null || BLANK.equals(url) ? "" : url);
        star.setText(!onStart && favoriteIndex(url) >= 0 ? "★ Favori" : "☆ Favori");
    }

    private void keyboard(boolean show) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (show) { address.requestFocus(); imm.showSoftInput(address, 0); }
        else { imm.hideSoftInputFromWindow(address.getWindowToken(), 0); address.clearFocus(); }
    }

    /** Typed text is an address when it looks like one, otherwise a web search. */
    static String resolve(String input) {
        String text = input == null ? "" : input.trim();
        if (text.isEmpty()) return null;
        if (text.startsWith("http://") || text.startsWith("https://")) return text;
        if (!text.contains(" ") && text.contains(".")) return "https://" + text;
        try { return SEARCH + java.net.URLEncoder.encode(text, "UTF-8"); } catch (Exception e) { return null; }
    }

    private void open(String input) {
        String url = resolve(input); keyboard(false);
        if (url != null) { onStart = false; web.loadUrl(url); }
    }

    // ---- Start page and favourites -------------------------------------------------------------

    private JSONArray favorites() {
        try { return new JSONArray(prefs.getString("favorites", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    private int favoriteIndex(String url) {
        if (url == null) return -1;
        JSONArray list = favorites();
        for (int i = 0; i < list.length(); i++) if (url.equals(list.optJSONObject(i) == null ? null : list.optJSONObject(i).optString("u"))) return i;
        return -1;
    }

    private void toggleFavorite() {
        String url = web.getUrl();
        if (onStart || url == null || !url.startsWith("http")) { Toast.makeText(this, "Ouvrez d’abord une page.", Toast.LENGTH_SHORT).show(); return; }
        try {
            JSONArray list = favorites(); int at = favoriteIndex(url);
            if (at >= 0) list.remove(at);
            else list.put(new JSONObject().put("t", title.isEmpty() ? url : title).put("u", url));
            prefs.edit().putString("favorites", list.toString()).apply();
            Toast.makeText(this, at >= 0 ? "Favori retiré" : "Ajouté aux favoris", Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) { }
        showAddress(url);
    }

    static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String card(String title, String url) {
        return "<a href=\"" + escape(url) + "\"><b>" + escape(title) + "</b><span>" + escape(url) + "</span></a>";
    }

    private void showStart() {
        StringBuilder html = new StringBuilder("<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width'>"
            + "<style>body{margin:0;padding:5vh 6vw;background:#0d0c12;color:#fff;font-family:sans-serif}"
            + "h1{font-size:44px;margin:0 0 8px}h2{font-size:24px;color:#c9c5d6;margin:36px 0 14px;font-weight:500}"
            + "p{font-size:22px;color:#c9c5d6;margin:0}.g{display:flex;flex-wrap:wrap;gap:18px}"
            + "a{display:block;box-sizing:border-box;width:calc(33.3% - 12px);padding:22px 26px;border-radius:22px;background:#262331;"
            + "border:2px solid #3a3747;color:#fff;text-decoration:none}a:hover{border-color:#e3e9f2;background:#343040}"
            + "b{display:block;font-size:25px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}"
            + "span{display:block;margin-top:6px;font-size:17px;color:#aaa6b8;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}</style>"
            + "<h1>Navigateur</h1><p>Flèches : déplacer le pointeur · OK : cliquer · Bord de l’écran : faire défiler · Retour : page précédente</p>");
        String last = prefs.getString("last", "");
        if (!last.isEmpty()) {
            String lastTitle = prefs.getString("last_title", "");
            html.append("<h2>Reprendre</h2><div class=g>").append(card(lastTitle.isEmpty() ? last : lastTitle, last)).append("</div>");
        }
        html.append("<h2>Favoris</h2>");
        JSONArray list = favorites();
        if (list.length() == 0) html.append("<p>Aucun favori pour l’instant. Ouvrez une page, puis choisissez « ☆ Favori » en haut à droite.</p>");
        else {
            html.append("<div class=g>");
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item != null) html.append(card(item.optString("t"), item.optString("u")));
            }
            html.append("</div>");
        }
        onStart = true; clearHistory = true; title = "";
        web.loadDataWithBaseURL(null, html.toString(), "text/html", "UTF-8", null);
    }

    /** Returns false when there is nowhere left to go back to. */
    private boolean back(boolean mayLeave) {
        if (customView != null) { leaveFullscreen(); return true; }
        WebBackForwardList history = web.copyBackForwardList(); int index = history.getCurrentIndex();
        if (index > 0 && !BLANK.equals(history.getItemAtIndex(index - 1).getUrl())) { web.goBack(); return true; }
        if (!onStart) { showStart(); return true; }
        if (mayLeave) finish();
        return false;
    }

    // ---- Remote control ------------------------------------------------------------------------

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN; int repeat = event.getRepeatCount();
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT: if (down) move(-1, 0, repeat); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: if (down) move(1, 0, repeat); return true;
            case KeyEvent.KEYCODE_DPAD_UP: if (down) move(0, -1, repeat); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: if (down) move(0, 1, repeat); return true;
            case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (address.hasFocus()) break; // A physical keyboard validates the address field.
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (down && repeat == 0) touch(MotionEvent.ACTION_DOWN); else if (!down) touch(MotionEvent.ACTION_UP);
                return true;
            case KeyEvent.KEYCODE_PAGE_DOWN: case KeyEvent.KEYCODE_CHANNEL_DOWN: if (down) { wake(); wheel(0, -8); } return true;
            case KeyEvent.KEYCODE_PAGE_UP: case KeyEvent.KEYCODE_CHANNEL_UP: if (down) { wake(); wheel(0, 8); } return true;
            case KeyEvent.KEYCODE_BACK: if (!down) { if (address.hasFocus()) keyboard(false); else back(true); } return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void wake() {
        pointer.animate().cancel(); pointer.setAlpha(1);
        pointer.removeCallbacks(hidePointer); pointer.postDelayed(hidePointer, 6000);
    }

    /** Moves the pointer; a held arrow accelerates, and pushing against an edge scrolls the page. */
    private void move(int dx, int dy, int repeat) {
        wake();
        float step = px(16) + Math.min(repeat, 22) * px(3.2f), push = 1 + Math.min(repeat, 12) * .35f;
        float width = content.getWidth(), height = content.getHeight();
        float top = customView != null ? 0 : web.getTop();
        float x = cx + dx * step, y = cy + dy * step;
        if (dy > 0 && y > height - 1) wheel(0, -push);
        if (dy < 0 && y < top && cy >= top && (customView != null || web.canScrollVertically(-1))) { wheel(0, push); y = top; }
        if (dx > 0 && x > width - 1) wheel(-push, 0);
        if (dx < 0 && x < 0) wheel(push, 0);
        cx = Math.max(0, Math.min(width - 1, x)); cy = Math.max(0, Math.min(height - 1, y));
        pointer.invalidate();
        if (pressedAt != 0) touch(MotionEvent.ACTION_MOVE); else pointerEvent(MotionEvent.ACTION_HOVER_MOVE, 0, 0);
    }

    /** OK is a real tap at the pointer: it reaches the toolbar, the page and a full-screen video alike. */
    private void touch(int action) {
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) { wake(); pressedAt = now; pointer.invalidate(); }
        else if (pressedAt == 0) return;
        MotionEvent event = MotionEvent.obtain(pressedAt, now, action, cx, cy, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        content.dispatchTouchEvent(event); event.recycle();
        if (action == MotionEvent.ACTION_UP) { pressedAt = 0; pointer.invalidate(); }
    }

    private void wheel(float horizontal, float vertical) { pointerEvent(MotionEvent.ACTION_SCROLL, horizontal, vertical); }

    /** Mouse-style hover and wheel events, so nested scrolling areas and hover menus respond too. */
    private void pointerEvent(int action, float horizontal, float vertical) {
        MotionEvent.PointerProperties[] properties = { new MotionEvent.PointerProperties() };
        properties[0].id = 0; properties[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords[] coords = { new MotionEvent.PointerCoords() };
        coords[0].x = cx; coords[0].y = cy;
        coords[0].setAxisValue(MotionEvent.AXIS_HSCROLL, horizontal); coords[0].setAxisValue(MotionEvent.AXIS_VSCROLL, vertical);
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, 1, properties, coords, 0, 0, 1, 1, 0, 0, InputDevice.SOURCE_MOUSE, 0);
        content.dispatchGenericMotionEvent(event); event.recycle();
    }

    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); if (web != null && !onStart) web.saveState(state); }
    @Override protected void onResume() { super.onResume(); if (web != null) { web.onResume(); wake(); } }
    @Override protected void onPause() { if (web != null) web.onPause(); super.onPause(); }
    @Override protected void onDestroy() {
        if (web != null) { leaveFullscreen(); column.removeView(web); web.destroy(); web = null; }
        super.onDestroy();
    }

    private final class Pointer extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arrow = new Path();
        Pointer() {
            super(BrowserActivity.this); setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            arrow.moveTo(0, 0); arrow.lineTo(0, 34); arrow.lineTo(9, 26); arrow.lineTo(15, 40); arrow.lineTo(21, 37); arrow.lineTo(15, 24); arrow.lineTo(26, 24); arrow.close();
        }
        @Override protected void onDraw(Canvas c) {
            c.save(); c.translate(cx, cy); c.scale(scale * 1.25f, scale * 1.25f);
            paint.setStyle(Paint.Style.FILL); paint.setColor(pressedAt != 0 ? 0xffb8f5c8 : Color.WHITE); c.drawPath(arrow, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.6f); paint.setStrokeJoin(Paint.Join.ROUND); paint.setColor(0xff0b0a10); c.drawPath(arrow, paint);
            c.restore();
        }
    }
}
