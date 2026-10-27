package dev.salontv;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** Calendar days in France, unaffected by daylight-saving hour changes. */
final class IaraCountdown {
    static final LocalDate TARGET=LocalDate.of(2027,1,30);
    static String today(){return label(LocalDate.now(ZoneId.of("Europe/Paris")));}
    static String label(LocalDate date){
        long days=ChronoUnit.DAYS.between(date,TARGET);
        return days>0?"J−"+days:days==0?"Jour J":"J+"+(-days);
    }
}
