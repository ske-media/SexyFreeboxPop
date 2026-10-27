package dev.salontv;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** One bounded HTTPS request off the UI thread, with persisted, timestamped fallback. */
final class WeatherRepository {
    static final TimeZone PARIS=TimeZone.getTimeZone("Europe/Paris");
    static final String ENDPOINT="https://api.open-meteo.com/v1/forecast?latitude=46.11106&longitude=5.97037&current=temperature_2m,weather_code,is_day&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=Europe%2FParis&forecast_days=7";
    static final long FRESH_MS=30*60*1000L;
    interface Listener { void onResult(Data data); }
    static final class Day {
        final String date;final double low,high;final int code;
        Day(String d,double l,double h,int c){date=d;low=l;high=h;code=c;}
    }
    static final class Data {
        final double temperature;final int code;final boolean daylight;final long fetchedAt;final List<Day> days;
        Data(double t,int c,boolean light,long at,List<Day> d){temperature=t;code=c;daylight=light;fetchedAt=at;days=d;}
    }
    private final SharedPreferences prefs;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private boolean busy=false;private long nextAttempt=0;
    WeatherRepository(Context c){prefs=c.getSharedPreferences("weather",Context.MODE_PRIVATE);}
    Data cached(){try{return parse(prefs.getString("json",""),prefs.getLong("fetched_at",0));}catch(Exception e){return null;}}
    synchronized void refresh(Listener listener) {
        long now=System.currentTimeMillis();Data cache=cached();
        if(busy || now<nextAttempt || (cache!=null && cache.days.get(0).date.equals(today()) && now-cache.fetchedAt>=0 && now-cache.fetchedAt<FRESH_MS))return;
        busy=true;
        worker.execute(()->{
            Data result=cache;HttpURLConnection conn=null;boolean success=false;
            try {
                conn=(HttpURLConnection)new URL(ENDPOINT).openConnection();
                conn.setConnectTimeout(7000);conn.setReadTimeout(7000);conn.setRequestProperty("User-Agent","SalonTV/0.2 (personal Android TV weather display)");
                if(conn.getResponseCode()!=200)throw new IOException("Weather HTTP "+conn.getResponseCode());
                StringBuilder body=new StringBuilder();
                try(Reader reader=new InputStreamReader(conn.getInputStream(),"UTF-8")){
                    char[] buf=new char[4096];int n;
                    while((n=reader.read(buf))!=-1){body.append(buf,0,n);if(body.length()>262144)throw new IOException("Weather response too large");}
                }
                long at=System.currentTimeMillis();result=parse(body.toString(),at);
                prefs.edit().putString("json",body.toString()).putLong("fetched_at",at).apply();success=true;
            } catch(Exception ignored) { /* Keep timestamped cached data; never invent conditions. */ }
            finally {if(conn!=null)conn.disconnect();synchronized(this){busy=false;nextAttempt=success?0:System.currentTimeMillis()+5*60*1000L;}}
            listener.onResult(result);
        });
    }
    void close(){worker.shutdownNow();}
    static Data parse(String json,long at)throws Exception {
        JSONObject root=new JSONObject(json),current=root.getJSONObject("current"),daily=root.getJSONObject("daily");
        double temperature=current.getDouble("temperature_2m");if(!Double.isFinite(temperature))throw new IOException("Invalid temperature");
        JSONArray dates=daily.getJSONArray("time"),lows=daily.getJSONArray("temperature_2m_min"),highs=daily.getJSONArray("temperature_2m_max"),codes=daily.getJSONArray("weather_code");
        if(dates.length()<7 || lows.length()!=dates.length() || highs.length()!=dates.length() || codes.length()!=dates.length())throw new IOException("Incomplete forecast");
        List<Day> days=new ArrayList<>();
        for(int i=0;i<dates.length();i++){
            double lo=lows.getDouble(i),hi=highs.getDouble(i);
            if(!Double.isFinite(lo)||!Double.isFinite(hi))throw new IOException("Invalid forecast");
            days.add(new Day(dates.getString(i),lo,hi,codes.getInt(i)));
        }
        return new Data(temperature,current.getInt("weather_code"),current.getInt("is_day")==1,at,days);
    }
    static String today(){SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);f.setTimeZone(PARIS);return f.format(new Date());}
    static String description(int code,boolean day) {
        if(code==0)return day?"Ciel dégagé":"Nuit dégagée";
        if(code==1)return "Peu nuageux";if(code==2)return "Éclaircies";if(code==3)return "Nuageux";
        if(code==45||code==48)return "Brouillard";
        if(code>=51&&code<=57)return "Bruine";
        if(code>=61&&code<=67)return "Pluie";
        if(code>=71&&code<=77)return "Neige";
        if(code>=80&&code<=82)return "Averses";
        if(code==85||code==86)return "Averses de neige";
        if(code>=95&&code<=99)return "Orages";
        return "Conditions variables";
    }
}
