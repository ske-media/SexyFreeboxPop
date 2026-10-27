package dev.salontv;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Small, offline-first TV home. Uses platform views only; no web runtime or polling. */
public final class HomeActivity extends Activity {
    private static final String FREE = "net.oqee.androidtv";
    private static final String NETFLIX = "com.netflix.ninja";
    private static final String YOUTUBE = "com.google.android.youtube.tv";
    private static final String AIRPLAY = "io.github.jqssun.airplay";
    private static final String ORIGINAL = "net.oqee.launcher";
    private final int WHITE = Color.WHITE, MUTED = Color.rgb(225,225,233);
    private FrameLayout root;
    private float scale = 1;
    private String page = "home";
    private int savedFocus = 0, nextFocus = 0, appPage = 0;
    private TextView clock,dateLabel,appHint,countdown;
    private FrameLayout dock;
    private View forecast;
    private boolean dockVisible;
    private Bitmap wallpaper;
    private WeatherRepository weather;
    private WeatherRepository.Data weatherData;
    private WeatherView weatherNow,weatherWeek;
    private final ArrayList<View> controls = new ArrayList<>();
    private final ArrayList<AppEntry> apps = new ArrayList<>();
    private boolean receiverRegistered;
    private final BroadcastReceiver minute = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { updateClock(); refreshWeather(); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        Point display = new Point(); getWindowManager().getDefaultDisplay().getRealSize(display);
        scale = Math.min(display.x / 1920f, display.y / 1080f);
        if (state != null) { savedFocus = state.getInt("focus",0); appPage = state.getInt("appPage",0); }
        int image=getResources().getIdentifier("wallpaper","drawable",getPackageName());
        wallpaper=BitmapFactory.decodeResource(getResources(),image);
        weather=new WeatherRepository(this);weatherData=weather.cached();
        showHome();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); savedFocus=0;
        if(page.equals("home") && root!=null) { updateClock(); revealDock(false); }
        else showHome();
    }
    @Override protected void onResume() {
        super.onResume(); updateClock(); refreshWeather();
        if (!receiverRegistered) { IntentFilter filter=new IntentFilter(Intent.ACTION_TIME_TICK);filter.addAction(Intent.ACTION_DATE_CHANGED);filter.addAction(Intent.ACTION_TIME_CHANGED);filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);registerReceiver(minute,filter); receiverRegistered=true; }
    }
    @Override protected void onPause() {
        super.onPause(); if (receiverRegistered) { unregisterReceiver(minute); receiverRegistered=false; }
    }
    @Override protected void onDestroy(){if(weather!=null)weather.close();super.onDestroy();}
    private void refreshWeather(){
        if(weather==null)return;
        weather.refresh(data->runOnUiThread(()->{
            if(isDestroyed())return;weatherData=data;
            if(weatherNow!=null)weatherNow.invalidate();if(weatherWeek!=null)weatherWeek.invalidate();
        }));
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(page.equals("home") && event.getAction()==KeyEvent.ACTION_DOWN){
            int key=event.getKeyCode();
            if(!dockVisible && (key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)){
                revealDock(true);return true;
            }
            if(dockVisible && key==KeyEvent.KEYCODE_DPAD_UP){revealDock(false);return true;}
        }
        return super.dispatchKeyEvent(event);
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putInt("focus",savedFocus); state.putInt("appPage",appPage);
    }
    @Override public void onBackPressed() {
        if (!page.equals("home")) showHome(); else if(dockVisible)revealDock(false);
    }
    private int px(float n) { return Math.round(n*scale); }
    private void place(View v,float x,float y,float w,float h) {
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(px(w),px(h)); lp.leftMargin=px(x);lp.topMargin=px(y);root.addView(v,lp);
    }
    private TextView text(String value,float size,int color,boolean bold) {
        TextView t=new TextView(this);t.setText(value);t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(size));t.setFontFeatureSettings("kern");
        t.setTypeface(Typeface.create("sans-serif-medium",bold?Typeface.BOLD:Typeface.NORMAL));
        t.setShadowLayer(px(8),0,px(2),0xa0000000);
        t.setIncludeFontPadding(false);t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private void label(String value,float x,float y,float w,float h,float size,int color,boolean bold) {
        place(text(value,size,color,bold),x,y,w,h);
    }
    private void base(String name) {
        page=name; controls.clear();nextFocus=0;
        root=new FrameLayout(this);root.setClipChildren(false);root.setClipToPadding(false);root.setBackground(new Backdrop());setContentView(root);
        weatherNow=null;weatherWeek=null;dateLabel=null;countdown=null;
        label("salon",112,80,250,60,42,WHITE,true);
        clock=text("",34,WHITE,false);clock.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);place(clock,1490,91,318,42);updateClock();
        String day=new SimpleDateFormat("EEEE d MMMM",Locale.FRANCE).format(new Date());
        dateLabel=text(day,20,MUTED,false);dateLabel.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);place(dateLabel,1400,141,408,32);
    }
    private String localTime(String pattern){SimpleDateFormat f=new SimpleDateFormat(pattern,Locale.FRANCE);f.setTimeZone(WeatherRepository.PARIS);return f.format(new Date());}
    private void updateClock() {
        if(clock!=null)clock.setText(localTime("HH:mm"));
        if(dateLabel!=null)dateLabel.setText(localTime("EEEE d MMMM"));
        if(countdown!=null)countdown.setText(IaraCountdown.today());
        if(weatherNow!=null)weatherNow.invalidate();if(weatherWeek!=null)weatherWeek.invalidate();
    }
    private void showHome() {
        page="home";controls.clear();nextFocus=0;dockVisible=false;
        root=new FrameLayout(this);root.setClipChildren(false);root.setClipToPadding(false);root.setBackground(new Backdrop());
        root.setFocusable(true);root.setFocusableInTouchMode(true);
        root.setContentDescription("Accueil. Appuyez sur Bas pour afficher les applications.");setContentView(root);
        dateLabel=text("",38,WHITE,true);place(dateLabel,112,80,1050,54);
        weatherNow=new WeatherView(false);place(weatherNow,112,151,760,150);
        clock=text("",252,WHITE,false);clock.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));clock.setGravity(Gravity.CENTER);
        clock.setFontFeatureSettings("tnum");clock.setShadowLayer(px(22),0,px(5),0x99000000);place(clock,360,390,1200,294);
        TextView occasion=text("Naissance Iara",31,WHITE,true);occasion.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);place(occasion,1330,80,478,54);
        countdown=text("",66,WHITE,true);countdown.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);place(countdown,1330,145,478,90);
        TextView target=text("30 janvier 2027",24,WHITE,false);target.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);place(target,1330,244,478,36);updateClock();
        weatherWeek=new WeatherView(true);forecast=weatherWeek;place(forecast,330,765,1260,190);
        appHint=text("⌄   Applications",27,WHITE,true);appHint.setGravity(Gravity.CENTER);place(appHint,660,1000,600,44);
        dock=new FrameLayout(this);dock.setClipChildren(false);dock.setClipToPadding(false);place(dock,236,812,1448,205);
        String[] names={"TV","Netflix","YouTube","Mirroir","Toutes les apps","Réglages"};
        String[] packages={FREE,NETFLIX,YOUTUBE,AIRPLAY};
        for(int i=0;i<6;i++){
            final int index=i;float x=i<4?i*250:1000+(i-4)*224;float w=i<4?226:200;
            FrameLayout tile=control(x,0,w,132,false,()->{
                savedFocus=index;
                if(index==3)showAirPlay();else if(index==4)showApps(0);else if(index==5)showSettings();else launch(packages[index]);
            });
            root.removeView(tile);dock.addView(tile);
            if(i<4){Brand brand=new Brand(i);FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(px(178),px(92));bp.gravity=Gravity.CENTER;tile.addView(brand,bp);}
            else {TextView icon=text(i==4?"⊞":"⚙",58,WHITE,false);icon.setGravity(Gravity.CENTER);tile.addView(icon,new FrameLayout.LayoutParams(-1,-1));}
            tile.setContentDescription(names[i]);
            TextView name=text(names[i],25,WHITE,true);name.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(px(w),px(40));lp.leftMargin=px(x);lp.topMargin=px(150);dock.addView(name,lp);
        }
        for(int i=0;i<6;i++){
            View v=controls.get(i);v.setNextFocusLeftId(controls.get(Math.max(0,i-1)).getId());v.setNextFocusRightId(controls.get(Math.min(5,i+1)).getId());v.setNextFocusDownId(v.getId());
        }
        dock.setVisibility(View.INVISIBLE);dock.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        dock.setTranslationY(px(250));dock.setAlpha(0);root.requestFocus();
    }
    private void revealDock(boolean visible){
        if(dock==null || !page.equals("home"))return;
        dockVisible=visible;
        dock.animate().cancel();clock.animate().cancel();forecast.animate().cancel();appHint.animate().cancel();
        dock.setVisibility(View.VISIBLE);dock.setDescendantFocusability(visible?android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS:android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        dock.animate().translationY(visible?0:px(250)).alpha(visible?1:0).setDuration(240).withEndAction(()->{if(!dockVisible)dock.setVisibility(View.INVISIBLE);}).start();
        clock.animate().translationY(visible?-px(198):0).scaleX(visible?.79f:1).scaleY(visible?.79f:1).setDuration(240).start();
        forecast.animate().translationY(visible?-px(205):0).setDuration(240).start();
        appHint.animate().alpha(visible?0:1).setDuration(160).start();
        if(visible)controls.get(Math.min(savedFocus,5)).requestFocus();else root.requestFocus();
    }
    private GradientDrawable surface(boolean focus,boolean pill) {
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            focus?new int[]{0xc0736570,0xc0393543}:new int[]{0x903a2e3d,0xa0252230});
        d.setCornerRadius(px(pill?34:27));d.setStroke(px(focus?2.5f:1),focus?0xffe3e9f2:0xff303744);return d;
    }
    private FrameLayout tile(float x,float y,float w,float h,Runnable action) { return control(x,y,w,h,false,action); }
    private FrameLayout control(float x,float y,float w,float h,boolean pill,Runnable action) {
        FrameLayout f=new FrameLayout(this);f.setId(View.generateViewId());f.setFocusable(true);f.setClickable(true);f.setBackground(surface(false,pill));
        f.setOnFocusChangeListener((v,focused)->{
            v.setBackground(surface(focused,pill));v.animate().scaleX(focused?1.035f:1f).scaleY(focused?1.035f:1f).setDuration(135).start();
            if(focused)v.bringToFront();
        });
        f.setOnClickListener(v->action.run());controls.add(f);nextFocus++;place(f,x,y,w,h);return f;
    }
    private void pill(String title,float x,float y,float width,Runnable action) {
        FrameLayout f=control(x,y,width,68,true,action);TextView t=text(title,24,WHITE,true);t.setGravity(Gravity.CENTER);
        f.addView(t,new FrameLayout.LayoutParams(-1,-1));f.setContentDescription(title);
    }
    private void loadApps() {
        apps.clear();PackageManager pm=getPackageManager();
        Intent q=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        HashSet<String> seen=new HashSet<>();
        for(ResolveInfo info:pm.queryIntentActivities(q,0)) {
            String pkg=info.activityInfo.packageName;
            if(pkg.equals(getPackageName())||pkg.equals(ORIGINAL)||!seen.add(pkg))continue;
            String title=pkg.equals(FREE)?"TV":pkg.equals(AIRPLAY)?"Mirroir":info.loadLabel(pm).toString();
            apps.add(new AppEntry(title,pkg,info.loadIcon(pm)));
        }
        Collections.sort(apps,(a,b)->a.title.compareToIgnoreCase(b.title));
    }
    private void showApps(int requested) {
        loadApps();appPage=Math.max(0,Math.min(requested,(Math.max(1,apps.size())-1)/10));base("apps");
        label("Vos applications",112,229,1500,91,68,WHITE,true);
        label("Tout votre univers, au même endroit.",116,340,1400,45,28,MUTED,false);
        int count=Math.min(10,apps.size()-appPage*10);
        for(int i=0;i<count;i++) {
            final AppEntry app=apps.get(appPage*10+i);int col=i%5,row=i/5;
            FrameLayout f=tile(112+col*347,439+row*213,310,146,()->launch(app.pkg));
            ImageView icon=new ImageView(this);icon.setImageDrawable(app.icon);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(px(92),px(92));ip.gravity=Gravity.CENTER;f.addView(icon,ip);
            f.setContentDescription(app.title);
            TextView name=text(app.title,21,WHITE,false);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            place(name,119+col*347,598+row*213,296,36);
        }
        pill("Retour",112,925,180,()->showHome());
        if(appPage>0)pill("Précédent",320,925,230,()->showApps(appPage-1));
        if((appPage+1)*10<apps.size())pill("Suivant",580,925,205,()->showApps(appPage+1));
        label((appPage+1)+" / "+Math.max(1,(apps.size()+9)/10),1660,935,145,40,24,MUTED,false);
        if(!controls.isEmpty())controls.get(0).requestFocus();
    }
    private void showAirPlay() {
        base("airplay");
        label("Votre écran, en grand.",112,230,1600,106,76,WHITE,true);
        label("Photos, musique et souvenirs à partager.",116,357,1500,52,31,MUTED,false);
        FrameLayout card=new FrameLayout(this);card.setBackground(surface(false,false));place(card,112,463,1696,345);
        TextView instructions=text("1   Connectez votre iPhone ou Mac au même réseau.\n\n2   Ouvrez Recopie de l’écran dans le centre de contrôle.\n\n3   Choisissez « TV du salon ».",30,WHITE,false);
        FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(-1,-1);ip.setMargins(px(44),px(26),px(44),px(26));card.addView(instructions,ip);
        pill("Ouvrir Mirroir",112,864,280,()->launch(AIRPLAY));
        pill("Retour",420,864,180,()->showHome());
        label("La disponibilité dépend du récepteur et du contenu partagé.",116,975,1500,34,21,MUTED,false);
        controls.get(0).requestFocus();
    }
    private void showSettings() {
        base("settings");label("À votre façon.",112,230,1500,106,76,WHITE,true);
        label("Les essentiels, simplement.",116,357,1500,52,31,MUTED,false);
        String[] titles={"Réglages du Player","Play Store","Mirroir","Réseau et connexion"};
        Runnable[] actions={()->{try{startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(Exception e){message("Réglages indisponibles");}},()->launch("com.android.vending"),()->launch(AIRPLAY),()->{try{startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));}catch(Exception e){message("Réglages indisponibles");}}};
        for(int i=0;i<4;i++) {
            FrameLayout f=tile(112+(i%2)*860,465+(i/2)*150,836,118,actions[i]);
            TextView t=text(titles[i]+"  ›",30,WHITE,false);t.setPadding(px(34),0,px(34),0);f.addView(t,new FrameLayout.LayoutParams(-1,-1));f.setContentDescription(titles[i]);
        }
        pill("Retour",112,921,180,()->showHome());controls.get(0).requestFocus();
    }
    private void launch(String pkg) {
        PackageManager pm=getPackageManager();Intent intent=pm.getLeanbackLaunchIntentForPackage(pkg);
        if(intent==null)intent=pm.getLaunchIntentForPackage(pkg);
        if(intent==null){message("Cette application n’est pas encore disponible.");return;}
        try{intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(intent);}catch(Exception e){message("Impossible d’ouvrir cette application.");}
    }
    private void message(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private static class AppEntry {
        final String title,pkg;final Drawable icon;
        AppEntry(String t,String p,Drawable d){title=t;pkg=p;icon=d;}
    }
    private class WeatherView extends View {
        private final boolean week;
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        WeatherView(boolean weekly){super(HomeActivity.this);week=weekly;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);setContentDescription(weekly?"Prévisions météo sur sept jours pour Valleiry":"Météo actuelle à Valleiry");}
        private void word(Canvas c,String value,float x,float y,float size,int color,Paint.Align align,boolean medium){
            p.setStyle(Paint.Style.FILL);p.setShader(null);p.setColor(color);p.setTextSize(size);p.setTextAlign(align);
            p.setTypeface(Typeface.create("sans-serif-medium",medium?Typeface.BOLD:Typeface.NORMAL));
            p.setShadowLayer(3,0,1,0x99000000);
            c.drawText(value,x,y,p);
            p.clearShadowLayer();
        }
        @Override protected void onDraw(Canvas original){
            original.save();original.scale(getWidth()/(week?1260f:760f),getHeight()/(week?190f:150f));Canvas c=original;
            WeatherRepository.Data data=weatherData;
            if(!week){
                word(c,"VALLEIRY · 74",0,23,23,WHITE,Paint.Align.LEFT,true);
                glyph(c,data==null?-1:data.code,data==null||data.daylight,33,82,56);
                word(c,data==null?"—°":Math.round(data.temperature)+"°",82,110,66,WHITE,Paint.Align.LEFT,true);
                String description=data==null?"Météo indisponible":WeatherRepository.description(data.code,data.daylight);
                word(c,description,260,82,29,WHITE,Paint.Align.LEFT,true);
                String detail="Prévisions locales";
                if(data!=null){
                    boolean old=System.currentTimeMillis()-data.fetchedAt>2*60*60*1000L;
                    SimpleDateFormat f=new SimpleDateFormat(old?"dd/MM à HH:mm":"HH:mm",Locale.FRANCE);f.setTimeZone(WeatherRepository.PARIS);
                    detail=(old?"Dernières données · ":"Actualisé à ")+f.format(new Date(data.fetchedAt));
                }
                word(c,detail,261,119,22,WHITE,Paint.Align.LEFT,false);
            } else {
                p.setColor(0x951b1724);p.setStyle(Paint.Style.FILL);c.drawRoundRect(0,0,1260,190,27,27,p);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(0x30ffffff);c.drawRoundRect(0.5f,0.5f,1259.5f,189.5f,27,27,p);p.setStyle(Paint.Style.FILL);
                ArrayList<WeatherRepository.Day> available=new ArrayList<>();
                if(data!=null)for(WeatherRepository.Day day:data.days)if(day.date.compareTo(WeatherRepository.today())>=0)available.add(day);
                Calendar date=Calendar.getInstance(WeatherRepository.PARIS,Locale.FRANCE);
                SimpleDateFormat dayName=new SimpleDateFormat("EEE",Locale.FRANCE);dayName.setTimeZone(WeatherRepository.PARIS);
                for(int i=0;i<7;i++){
                    WeatherRepository.Day d=i<available.size()?available.get(i):null;
                    String name=i==0?"Aujourd’hui":dayName.format(date.getTime()).replace(".","");
                    float x=90+i*180;
                    word(c,name,x,35,23,WHITE,Paint.Align.CENTER,true);
                    glyph(c,d==null?-1:d.code,true,x,79,39);
                    word(c,d==null?"—":Math.round(d.high)+"°",x-6,143,30,WHITE,Paint.Align.RIGHT,true);
                    word(c,d==null?"—":Math.round(d.low)+"°",x+7,143,28,0xffe9e3ee,Paint.Align.LEFT,false);
                    date.add(Calendar.DAY_OF_MONTH,1);
                }
                word(c,"Open-Meteo",1234,179,15,0xffe5dfe6,Paint.Align.RIGHT,false);
            }
            original.restore();
        }
        private void glyph(Canvas c,int code,boolean day,float x,float y,float size){
            c.save();c.translate(x-size/2,y-size/2);c.scale(size/64,size/64);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.6f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setColor(0xfff7eee5);
            if(code<0){c.drawLine(21,32,43,32,p);c.restore();return;}
            boolean sunny=code<=2;
            if(sunny){
                if(!day && code==0){
                    Path moon=new Path();moon.addCircle(32,29,17,Path.Direction.CW);Path cut=new Path();cut.addCircle(40,21,17,Path.Direction.CW);moon.op(cut,Path.Op.DIFFERENCE);p.setStyle(Paint.Style.FILL);c.drawPath(moon,p);p.setStyle(Paint.Style.STROKE);
                }else{
                    float sx=code==0?32:23,sy=code==0?30:23,r=code==0?12:9;
                    c.drawCircle(sx,sy,r,p);
                    for(int i=0;i<8;i++){double a=i*Math.PI/4;c.drawLine(sx+(float)Math.cos(a)*(r+5),sy+(float)Math.sin(a)*(r+5),sx+(float)Math.cos(a)*(r+10),sy+(float)Math.sin(a)*(r+10),p);}
                }
                if(code==0){c.restore();return;}
            }
            Path cloud=new Path();cloud.moveTo(15,44);cloud.cubicTo(2,43,6,25,18,28);cloud.cubicTo(20,10,46,13,47,29);cloud.cubicTo(62,25,65,43,52,44);cloud.close();
            if(sunny){p.setStyle(Paint.Style.FILL);p.setColor(0xffd9cbd4);c.drawPath(cloud,p);p.setColor(0xfff7eee5);p.setStyle(Paint.Style.STROKE);}
            c.drawPath(cloud,p);
            if(code==45||code==48){c.drawLine(12,51,53,51,p);c.drawLine(18,58,46,58,p);}
            else if((code>=71&&code<=77)||code==85||code==86){for(int i=0;i<3;i++){float sx=19+i*14;c.drawLine(sx,49,sx,59,p);c.drawLine(sx-4,51,sx+4,57,p);c.drawLine(sx-4,57,sx+4,51,p);}}
            else if(code>=95){Path bolt=new Path();bolt.moveTo(33,44);bolt.lineTo(26,54);bolt.lineTo(34,54);bolt.lineTo(29,63);c.drawPath(bolt,p);}
            else if(code>=51){for(int i=0;i<3;i++)c.drawLine(20+i*14,49,16+i*14,57,p);}
            c.restore();
        }
    }
    private class Backdrop extends Drawable {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas c) {
            c.drawColor(0xff191420);
            if(wallpaper!=null){
                float factor=Math.max(getBounds().width()/(float)wallpaper.getWidth(),getBounds().height()/(float)wallpaper.getHeight());
                float width=wallpaper.getWidth()*factor,height=wallpaper.getHeight()*factor;
                p.setFilterBitmap(true);c.drawBitmap(wallpaper,null,new RectF((getBounds().width()-width)/2,(getBounds().height()-height)/2,(getBounds().width()+width)/2,(getBounds().height()+height)/2),p);
            }
            p.setShader(new LinearGradient(0,0,0,getBounds().height(),new int[]{0x90090b13,0x20090b13,0x30090b13,0xc0090b13},new float[]{0,.40f,.60f,1},Shader.TileMode.CLAMP));
            c.drawRect(getBounds(),p);p.setShader(null);
            if(!page.equals("home")){p.setColor(0xb5090c15);c.drawRect(getBounds(),p);}
        }
        @Override public void setAlpha(int a){} @Override public void setColorFilter(ColorFilter f){} @Override public int getOpacity(){return PixelFormat.OPAQUE;}
    }
    private class Brand extends View {
        final int brand;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Brand(int n){super(HomeActivity.this);brand=n;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas raw) {
            raw.save();raw.scale(getWidth()/300f,getHeight()/130f);Canvas c=raw;
            p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
            if(brand==0){p.setColor(0xfffafafa);p.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));p.setTextSize(67);c.drawText("TV",150,89,p);}
            else if(brand==1){p.setColor(0xffe50914);p.setTextSize(47);p.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));c.drawText("NETFLIX",150,84,p);}
            else if(brand==2){p.setColor(0xffff0033);c.drawRoundRect(35,36,121,96,17,17,p);p.setColor(Color.WHITE);Path play=new Path();play.moveTo(69,51);play.lineTo(69,81);play.lineTo(94,66);play.close();c.drawPath(play,p);p.setTextSize(36);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));c.drawText("YouTube",208,80,p);}
            else {p.setColor(0xfff4f6fa);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3.5f);c.drawRoundRect(115,24,185,71,5,5,p);p.setStyle(Paint.Style.FILL);Path tri=new Path();tri.moveTo(150,57);tri.lineTo(130,82);tri.lineTo(170,82);tri.close();c.drawPath(tri,p);p.setTextSize(23);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));c.drawText("Mirroir",150,113,p);}
            raw.restore();
        }
    }
}
