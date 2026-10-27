package dev.salontv;
import java.time.LocalDate;

public final class IaraCountdownTest {
    static void expect(String date,String value){
        String actual=IaraCountdown.label(LocalDate.parse(date));
        if(!value.equals(actual))throw new AssertionError(date+": "+actual+" != "+value);
    }
    public static void main(String[] args){
        expect("2026-10-27","J−95");
        expect("2026-12-31","J−30");
        expect("2027-01-29","J−1");
        expect("2027-01-30","Jour J");
        expect("2027-01-31","J+1");
        System.out.println("PASS: countdown, year change, target day and following day");
    }
}
