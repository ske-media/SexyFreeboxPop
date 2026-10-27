package dev.salontv;

import org.json.*;

/** Protocol boundary checks. Fixtures are synthetic and never shown in the app. */
public class WeatherRepositoryTest {
    static JSONObject fixture() throws Exception {
        JSONObject current=new JSONObject().put("temperature_2m",-3.5).put("weather_code",71).put("is_day",0);
        JSONArray dates=new JSONArray(),low=new JSONArray(),high=new JSONArray(),codes=new JSONArray();
        for(int i=0;i<7;i++){dates.put("2026-10-"+(21+i));low.put(-6);high.put(2);codes.put(71);}
        JSONObject daily=new JSONObject().put("time",dates).put("temperature_2m_min",low).put("temperature_2m_max",high).put("weather_code",codes);
        return new JSONObject().put("current",current).put("daily",daily);
    }
    static void rejects(JSONObject input) throws Exception {
        try{WeatherRepository.parse(input.toString(),1234);throw new AssertionError("Malformed forecast accepted");}catch(AssertionError e){throw e;}catch(Exception expected){}
    }
    public static void main(String[] args) throws Exception {
        WeatherRepository.Data data=WeatherRepository.parse(fixture().toString(),1234);
        if(data.temperature!=-3.5||data.days.size()!=7||data.daylight||data.fetchedAt!=1234)throw new AssertionError("Forecast values changed");
        if(data.days.get(0).low!=-6||data.days.get(0).high!=2)throw new AssertionError("Daily ranges changed");
        JSONObject missing=fixture();missing.getJSONObject("current").put("temperature_2m",JSONObject.NULL);rejects(missing);
        JSONObject incomplete=fixture();incomplete.getJSONObject("daily").getJSONArray("time").remove(6);rejects(incomplete);
        JSONObject noDaily=fixture();noDaily.remove("daily");rejects(noDaily);
        if(!WeatherRepository.description(0,false).equals("Nuit dégagée"))throw new AssertionError("Night clear sky");
        if(!WeatherRepository.description(95,true).equals("Orages"))throw new AssertionError("Thunderstorm mapping");
        if(!WeatherRepository.PARIS.getID().equals("Europe/Paris"))throw new AssertionError("Wrong timezone");
        System.out.println("PASS: seven-day forecast, negative temperatures, timestamps, invalid/null data, weather codes and timezone");
    }
}
