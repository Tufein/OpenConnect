// Generates the FIT fixtures in android/core/src/test/resources/watch/ with Garmin's own FIT SDK, so the
// tests read files written by the reference encoder instead of by our code, and reads them back with
// the SDK decoder to prove they are valid. The values the tests expect are the ones set below.
//
// Not part of the build and not shipped: the SDK is under Garmin's FIT license and is only used here to
// produce data files. Run (JDK 17+, Maven Central access):
//   curl -LO https://repo1.maven.org/maven2/com/garmin/fit/21.221.0/fit-21.221.0.jar
//   javac -cp fit-21.221.0.jar WatchFixtures.java && java -cp fit-21.221.0.jar:. WatchFixtures <output dir>
import com.garmin.fit.*;
import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class WatchFixtures {
    static final long FIT_EPOCH = 631_065_600L;
    static final long OFFSET = 7200; // the watch is on UTC+2 (CEST)

    static long fit(String iso) { return Instant.parse(iso).getEpochSecond() - FIT_EPOCH; }

    static FileIdMesg fileId(int type) {
        FileIdMesg m = new FileIdMesg();
        m.setFieldValue(0, 0, type, Fit.SUBFIELD_INDEX_MAIN_FIELD);
        m.setManufacturer(Manufacturer.GARMIN);
        m.setProduct(4440);
        m.setSerialNumber(3_456_789_012L);
        m.setTimeCreated(new DateTime(fit("2026-06-09T22:00:00Z")));
        return m;
    }

    /** Monitoring message with a full timestamp or only the low 16 bits, like a real watch writes them. */
    static MonitoringMesg mon(String iso, boolean full) {
        MonitoringMesg m = new MonitoringMesg();
        long t = fit(iso);
        if (full) m.setTimestamp(new DateTime(t)); else m.setTimestamp16((int) (t & 0xFFFF));
        return m;
    }

    static StressLevelMesg stress(String iso, int value) {
        StressLevelMesg m = new StressLevelMesg();
        m.setStressLevelTime(new DateTime(fit(iso)));
        m.setStressLevelValue((short) value);
        return m;
    }

    static void monitoring(File out) throws Exception {
        FileEncoder enc = new FileEncoder(out, Fit.ProtocolVersion.V2_0);
        enc.write(fileId(32)); // monitoring_b

        MonitoringInfoMesg info = new MonitoringInfoMesg();
        info.setTimestamp(new DateTime(fit("2026-06-09T22:00:00Z")));
        info.setLocalTimestamp(fit("2026-06-09T22:00:00Z") + OFFSET);
        info.setActivityType(0, ActivityType.WALKING);
        info.setActivityType(1, ActivityType.RUNNING);
        info.setCyclesToDistance(0, 0.76f);
        info.setCyclesToDistance(1, 1.1f);
        info.setCyclesToCalories(0, 0.04f);
        info.setCyclesToCalories(1, 0.09f);
        info.setRestingMetabolicRate(1650);
        enc.write(info);

        MonitoringHrDataMesg rhr = new MonitoringHrDataMesg();
        rhr.setTimestamp(new DateTime(fit("2026-06-10T04:00:00Z")));
        rhr.setRestingHeartRate((short) 55);
        rhr.setCurrentDayRestingHeartRate((short) 54);
        enc.write(rhr);

        // local 2026-06-10
        MonitoringMesg a = mon("2026-06-10T04:00:00Z", true);
        a.setActivityType(ActivityType.WALKING); a.setSteps(500L); a.setDistance(380f); a.setActiveCalories(21);
        enc.write(a);
        MonitoringMesg b = mon("2026-06-10T04:00:30Z", false); b.setHeartRate((short) 62); enc.write(b);
        MonitoringMesg c = mon("2026-06-10T04:05:00Z", false); c.setHeartRate((short) 58); enc.write(c);
        MonitoringMesg d = mon("2026-06-10T04:15:00Z", false);
        d.setActivityType(ActivityType.WALKING); d.setSteps(1500L); d.setDistance(1140f); d.setActiveCalories(63);
        enc.write(d);
        MonitoringMesg e = mon("2026-06-10T04:15:10Z", false);
        e.setActivityType(ActivityType.RUNNING); e.setSteps(200L); e.setDistance(240f); e.setActiveCalories(30);
        enc.write(e);
        MonitoringMesg f = mon("2026-06-10T10:00:00Z", true);
        f.setActivityType(ActivityType.WALKING); f.setSteps(6000L); f.setDistance(4560f); f.setActiveCalories(250);
        f.setAscent(61f);
        enc.write(f);
        MonitoringMesg g = mon("2026-06-10T10:00:30Z", false); g.setHeartRate((short) 90); enc.write(g);
        MonitoringMesg h = mon("2026-06-10T10:05:00Z", false); h.setHeartRate((short) 110); enc.write(h);
        MonitoringMesg i = mon("2026-06-10T10:10:00Z", false);
        i.setModerateActivityMinutes(10); i.setVigorousActivityMinutes(5);
        enc.write(i);
        MonitoringMesg j = mon("2026-06-10T21:30:00Z", true);
        j.setActivityType(ActivityType.WALKING); j.setSteps(9000L); j.setDistance(6840f); j.setActiveCalories(380);
        enc.write(j);
        MonitoringMesg j2 = mon("2026-06-10T21:30:10Z", false);
        j2.setActivityType(ActivityType.RUNNING); j2.setSteps(1200L); j2.setDistance(1320f); j2.setActiveCalories(110);
        enc.write(j2);
        MonitoringMesg k = mon("2026-06-10T21:31:00Z", false);
        k.setHeartRate((short) 70); k.setModerateActivityMinutes(25); k.setVigorousActivityMinutes(12);
        enc.write(k);

        enc.write(stress("2026-06-10T06:00:00Z", 20));
        enc.write(stress("2026-06-10T07:00:00Z", 30));
        enc.write(stress("2026-06-10T11:00:00Z", -2)); // not measured (activity or off wrist)
        enc.write(stress("2026-06-10T12:00:00Z", 60));
        enc.write(stress("2026-06-10T13:00:00Z", 50));

        Spo2DataMesg s1 = new Spo2DataMesg(); s1.setTimestamp(new DateTime(fit("2026-06-10T01:00:00Z"))); s1.setReadingSpo2((short) 96); enc.write(s1);
        Spo2DataMesg s2 = new Spo2DataMesg(); s2.setTimestamp(new DateTime(fit("2026-06-10T02:00:00Z"))); s2.setReadingSpo2((short) 94); enc.write(s2);
        Spo2DataMesg s3 = new Spo2DataMesg(); s3.setTimestamp(new DateTime(fit("2026-06-10T03:00:00Z"))); s3.setReadingSpo2((short) 98); enc.write(s3);
        RespirationRateMesg r1 = new RespirationRateMesg(); r1.setTimestamp(new DateTime(fit("2026-06-10T01:00:00Z"))); r1.setRespirationRate(14.5f); enc.write(r1);
        RespirationRateMesg r2 = new RespirationRateMesg(); r2.setTimestamp(new DateTime(fit("2026-06-10T02:00:00Z"))); r2.setRespirationRate(15.5f); enc.write(r2);

        // local 2026-06-11: the counters start again after local midnight
        MonitoringMesg l = mon("2026-06-10T22:10:00Z", true);
        l.setActivityType(ActivityType.WALKING); l.setSteps(40L); l.setDistance(30f); l.setActiveCalories(2);
        enc.write(l);
        MonitoringMesg l2 = mon("2026-06-10T22:10:30Z", false); l2.setHeartRate((short) 55); enc.write(l2);
        enc.write(stress("2026-06-10T22:30:00Z", 10));
        enc.close();
    }

    static void sleep(File out) throws Exception {
        FileEncoder enc = new FileEncoder(out, Fit.ProtocolVersion.V2_0);
        enc.write(fileId(49)); // sleep
        String[] when = {
            "2026-06-10T21:00:00Z", "2026-06-10T21:10:00Z", "2026-06-10T23:10:00Z", "2026-06-11T00:30:00Z",
            "2026-06-11T02:00:00Z", "2026-06-11T02:05:00Z", "2026-06-11T03:45:00Z", "2026-06-11T04:25:00Z",
        };
        SleepLevel[] level = {
            SleepLevel.AWAKE, SleepLevel.LIGHT, SleepLevel.DEEP, SleepLevel.REM,
            SleepLevel.AWAKE, SleepLevel.LIGHT, SleepLevel.REM, SleepLevel.AWAKE,
        };
        for (int n = 0; n < when.length; n++) {
            SleepLevelMesg m = new SleepLevelMesg();
            m.setTimestamp(new DateTime(fit(when[n])));
            m.setSleepLevel(level[n]);
            enc.write(m);
        }
        SleepAssessmentMesg sa = new SleepAssessmentMesg();
        sa.setOverallSleepScore((short) 82);
        sa.setAverageStressDuringSleep(21f);
        enc.write(sa);
        HrvStatusSummaryMesg hs = new HrvStatusSummaryMesg();
        hs.setTimestamp(new DateTime(fit("2026-06-11T04:30:00Z")));
        hs.setWeeklyAverage(48f); hs.setLastNightAverage(52f); hs.setLastNight5MinHigh(70f);
        hs.setStatus(HrvStatus.BALANCED);
        enc.write(hs);
        int[] hrv = {45, 55, 60};
        for (int n = 0; n < hrv.length; n++) {
            HrvValueMesg v = new HrvValueMesg();
            v.setTimestamp(new DateTime(fit("2026-06-10T23:00:00Z") + n * 300L));
            v.setValue((float) hrv[n]);
            enc.write(v);
        }
        enc.close();
    }

    static void hsa(File out) throws Exception {
        FileEncoder enc = new FileEncoder(out, Fit.ProtocolVersion.V2_0);
        enc.write(fileId(0));
        HsaBodyBatteryDataMesg b1 = new HsaBodyBatteryDataMesg();
        b1.setTimestamp(new DateTime(fit("2026-06-10T04:00:00Z")));
        b1.setProcessingInterval(180);
        byte[] l1 = {90, 88, 85, 80};
        for (int n = 0; n < l1.length; n++) b1.setLevel(n, l1[n]);
        enc.write(b1);
        HsaBodyBatteryDataMesg b2 = new HsaBodyBatteryDataMesg();
        b2.setTimestamp(new DateTime(fit("2026-06-10T10:00:00Z")));
        b2.setProcessingInterval(180);
        byte[] l2 = {70, 65, 60, -16}; // -16: no reading
        for (int n = 0; n < l2.length; n++) b2.setLevel(n, l2[n]);
        enc.write(b2);
        HsaStressDataMesg st = new HsaStressDataMesg();
        st.setTimestamp(new DateTime(fit("2026-06-10T04:00:00Z")));
        st.setProcessingInterval(180);
        byte[] sv = {25, 30, 40, -1};
        for (int n = 0; n < sv.length; n++) st.setStressLevel(n, sv[n]);
        enc.write(st);
        HsaHeartRateDataMesg hr = new HsaHeartRateDataMesg();
        hr.setTimestamp(new DateTime(fit("2026-06-10T04:00:00Z")));
        hr.setProcessingInterval(60);
        short[] hv = {61, 59, 0, 64};
        for (int n = 0; n < hv.length; n++) hr.setHeartRate(n, hv[n]);
        enc.write(hr);
        enc.close();
    }

    /**
     * Heart-rate messages that carry only the low 16 bits of their time, straddling the point where those
     * 16 bits start over. The full timestamp before them is 36 seconds short of the rollover.
     */
    static long wrapBase() {
        long base = fit("2026-06-10T04:00:00Z");
        long t = base - (base & 0xFFFF) + 65500;
        return t < base ? t + 65536 : t;
    }

    static void wrap(File out) throws Exception {
        FileEncoder enc = new FileEncoder(out, Fit.ProtocolVersion.V2_0);
        enc.write(fileId(32));
        long t = wrapBase();
        MonitoringInfoMesg info = new MonitoringInfoMesg();
        info.setTimestamp(new DateTime(t - 10));
        info.setLocalTimestamp(t - 10 + OFFSET);
        enc.write(info);
        MonitoringMesg full = new MonitoringMesg();
        full.setTimestamp(new DateTime(t));
        full.setActivityType(ActivityType.WALKING);
        full.setSteps(100L);
        enc.write(full);
        long[] after = {20, 60, 400};
        short[] hr = {60, 61, 62};
        for (int n = 0; n < after.length; n++) {
            MonitoringMesg m = new MonitoringMesg();
            m.setTimestamp16((int) ((t + after[n]) & 0xFFFF));
            m.setHeartRate(hr[n]);
            enc.write(m);
        }
        enc.close();
    }

    /** Reads a file back with the SDK's decoder: proves the fixture is a valid FIT file (CRC included). */
    static void check(File file) throws Exception {
        Decode decode = new Decode();
        MesgBroadcaster broadcaster = new MesgBroadcaster(decode);
        final int[] count = {0};
        broadcaster.addListener((MesgListener) m -> count[0]++);
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            if (!decode.read(in, broadcaster, broadcaster)) throw new IllegalStateException("SDK could not read " + file);
        }
        System.out.println(file.getName() + ": " + count[0] + " messages, valid FIT");
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : ".");
        dir.mkdirs();
        File monitoring = new File(dir, "monitoring.fit");
        monitoring(monitoring);
        sleep(new File(dir, "sleep.fit"));
        hsa(new File(dir, "hsa.fit"));
        wrap(new File(dir, "wrap.fit"));
        System.out.println("wrap.fit full timestamp (FIT epoch seconds): " + wrapBase());
        for (String name : new String[] {"monitoring.fit", "sleep.fit", "hsa.fit", "wrap.fit"}) check(new File(dir, name));
    }
}
