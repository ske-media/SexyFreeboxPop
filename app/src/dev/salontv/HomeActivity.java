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
    private final int WHITE = Color.rgb(245,246,248), MUTED = Color.rgb(149,155,166);
    private FrameLayout root;
    private float scale = 1;
    private String page = "home";
    private int savedFocus = 0, nextFocus = 0, appPage = 0;
    private TextView clock;
    private final ArrayList<View> controls = new ArrayList<>();
    private final ArrayList<AppEntry> apps = new ArrayList<>();
    private boolean receiverRegistered;
    private final BroadcastReceiver minute = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { updateClock(); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        Point display = new Point(); getWindowManager().getDefaultDisplay().getRealSize(display);
        scale = Math.min(display.x / 1920f, display.y / 1080f);
        if (state != null) { savedFocus = state.getInt("focus",0); appPage = state.getInt("appPage",0); }
        showHome();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); savedFocus=0;
        if(page.equals("home") && root!=null && !controls.isEmpty()) { updateClock(); controls.get(0).requestFocus(); }
        else showHome();
    }
    @Override protected void onResume() {
        super.onResume(); updateClock();
        if (!receiverRegistered) { registerReceiver(minute,new IntentFilter(Intent.ACTION_TIME_TICK)); receiverRegistered=true; }
    }
    @Override protected void onPause() {
        super.onPause(); if (receiverRegistered) { unregisterReceiver(minute); receiverRegistered=false; }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putInt("focus",savedFocus); state.putInt("appPage",appPage);
    }
    @Override public void onBackPressed() {
        if (!page.equals("home")) showHome(); else if (!controls.isEmpty()) controls.get(0).requestFocus();
    }
    private int px(float n) { return Math.round(n*scale); }
    private void place(View v,float x,float y,float w,float h) {
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(px(w),px(h)); lp.leftMargin=px(x);lp.topMargin=px(y);root.addView(v,lp);
    }
    private TextView text(String value,float size,int color,boolean bold) {
        TextView t=new TextView(this);t.setText(value);t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(size));t.setFontFeatureSettings("kern");
        t.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
        t.setIncludeFontPadding(false);t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private void label(String value,float x,float y,float w,float h,float size,int color,boolean bold) {
        place(text(value,size,color,bold),x,y,w,h);
    }
    private void base(String name) {
        page=name; controls.clear();nextFocus=0;
        root=new FrameLayout(this);root.setClipChildren(false);root.setClipToPadding(false);root.setBackground(new Backdrop());setContentView(root);
        label("salon",112,80,250,60,42,WHITE,true);
        clock=text("",34,WHITE,false);clock.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);place(clock,1490,91,318,42);updateClock();
        String day=new SimpleDateFormat("EEEE d MMMM",Locale.FRANCE).format(new Date());
        TextView date=text(day,20,MUTED,false);date.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);place(date,1400,141,408,32);
    }
    private void updateClock() { if(clock!=null) clock.setText(new SimpleDateFormat("HH:mm",Locale.FRANCE).format(new Date())); }
    private void showHome() {
        base("home");
        Calendar now=Calendar.getInstance();int h=now.get(Calendar.HOUR_OF_DAY);
        label(h>=18||h<5?"Bonsoir.":"Bonjour.",112,227,1500,113,94,WHITE,true);
        label("Installez-vous. Tout est ici.",116,362,1400,50,32,MUTED,false);
        String[] title={"Free TV","Netflix","YouTube","AirPlay"};
        String[] sub={"Le direct et le replay","Films et séries","À voir et à découvrir","Votre écran, en grand"};
        String[] packages={FREE,NETFLIX,YOUTUBE,AIRPLAY};
        for(int i=0;i<4;i++) {
            final String pkg=packages[i];final int index=i;
            FrameLayout tile=tile(112+i*430,506,406,232,()->{savedFocus=index;if(pkg.equals(AIRPLAY))showAirPlay();else launch(pkg);});
            Brand brand=new Brand(i);FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(px(300),px(130));bp.gravity=Gravity.CENTER;tile.addView(brand,bp);
            tile.setContentDescription(title[i]+". "+sub[i]);
            label(title[i],120+i*430,767,398,39,28,WHITE,true);
            label(sub[i],120+i*430,817,398,34,22,MUTED,false);
        }
        pill("Toutes les apps",112,919,290,()->showApps(0));
        pill("Réglages",426,919,213,()->showSettings());
        TextView hint=text("Un instant pour vous.",20,Color.rgb(100,108,120),false);hint.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);place(hint,1390,935,418,32);
        // Explicit focus paths keep the four main services in one predictable row.
        for(int i=0;i<4;i++) {
            View v=controls.get(i);v.setNextFocusLeftId(controls.get(Math.max(0,i-1)).getId());v.setNextFocusRightId(controls.get(Math.min(3,i+1)).getId());
            v.setNextFocusDownId(controls.get(i<2?4:5).getId());v.setNextFocusUpId(v.getId());
        }
        for(int i=4;i<6;i++){controls.get(i).setNextFocusUpId(controls.get(i==4?0:3).getId());controls.get(i).setNextFocusDownId(controls.get(i).getId());}
        controls.get(4).setNextFocusLeftId(controls.get(4).getId());controls.get(4).setNextFocusRightId(controls.get(5).getId());
        controls.get(5).setNextFocusLeftId(controls.get(4).getId());controls.get(5).setNextFocusRightId(controls.get(5).getId());
        controls.get(Math.min(savedFocus,3)).requestFocus();
    }
    private GradientDrawable surface(boolean focus,boolean pill) {
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            focus?new int[]{0xff343e4d,0xff232a35}:new int[]{0xff1d232d,0xff171c24});
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
            apps.add(new AppEntry(info.loadLabel(pm).toString(),pkg,info.loadIcon(pm)));
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
        pill("Ouvrir AirPlay",112,864,280,()->launch(AIRPLAY));
        pill("Retour",420,864,180,()->showHome());
        label("La disponibilité dépend du récepteur et du contenu partagé.",116,975,1500,34,21,MUTED,false);
        controls.get(0).requestFocus();
    }
    private void showSettings() {
        base("settings");label("À votre façon.",112,230,1500,106,76,WHITE,true);
        label("Les essentiels, simplement.",116,357,1500,52,31,MUTED,false);
        String[] titles={"Réglages du Player","Play Store","Récepteur AirPlay","Réseau et connexion"};
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
    private class Backdrop extends Drawable {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas c) {
            c.drawColor(0xff090c12);
            p.setShader(new RadialGradient(px(1290),px(120),px(1250),new int[]{0xff202c40,0xff111823,0xff090c12},new float[]{0,.49f,1},Shader.TileMode.CLAMP));
            c.drawRect(getBounds(),p);p.setShader(null);
            p.setColor(0xff28303d);p.setStrokeWidth(px(1));c.drawLine(px(112),px(190),px(1808),px(190),p);
        }
        @Override public void setAlpha(int a){} @Override public void setColorFilter(ColorFilter f){} @Override public int getOpacity(){return PixelFormat.OPAQUE;}
    }
    private class Brand extends View {
        final int brand;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Brand(int n){super(HomeActivity.this);brand=n;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas raw) {
            raw.save();raw.scale(getWidth()/300f,getHeight()/130f);Canvas c=raw;
            p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
            if(brand==0){p.setColor(0xfffafafa);p.setTextSize(56);p.setTypeface(Typeface.create("sans-serif",Typeface.ITALIC));c.drawText("free",118,84,p);p.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));p.setTextSize(48);c.drawText("TV",222,84,p);}
            else if(brand==1){p.setColor(0xffe50914);p.setTextSize(47);p.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));c.drawText("NETFLIX",150,84,p);}
            else if(brand==2){p.setColor(0xffff0033);c.drawRoundRect(35,36,121,96,17,17,p);p.setColor(Color.WHITE);Path play=new Path();play.moveTo(69,51);play.lineTo(69,81);play.lineTo(94,66);play.close();c.drawPath(play,p);p.setTextSize(36);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));c.drawText("YouTube",208,80,p);}
            else {p.setColor(0xfff4f6fa);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3.5f);c.drawRoundRect(115,24,185,71,5,5,p);p.setStyle(Paint.Style.FILL);Path tri=new Path();tri.moveTo(150,57);tri.lineTo(130,82);tri.lineTo(170,82);tri.close();c.drawPath(tri,p);p.setTextSize(23);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));c.drawText("AirPlay",150,113,p);}
            raw.restore();
        }
    }
}
