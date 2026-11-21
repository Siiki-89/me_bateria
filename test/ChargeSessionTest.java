package br.com.memotos.bateria;

public class ChargeSessionTest {
    public static void main(String[] args) {
        var tracker = new ChargeSession();
        check(tracker.sample(0, 60, 0) == null);
        check(tracker.sample(1000, 60, 2) == null);
        check(tracker.sample(4000, 60, 2) == null);
        check(tracker.sample(7000, 60, 0) == null);
        check(tracker.sample(10000, 60, 0) == null);
        var result = tracker.sample(13000, 60, 0);
        check(result.durationMs() == 6000 && result.peakW() == 120);
        check(Math.abs(result.energyWh() - 0.15) < 0.000001 && !result.interrupted());
        check(tracker.sample(16000, 60, 0) == null);
        tracker.sample(19000, 60, 1);
        tracker.sample(22000, 60, 1);
        check(tracker.interrupt().interrupted());
        check(tracker.interrupt() == null);
        tracker.sample(25000, 60, 1);
        check(tracker.sample(50000, 60, 1).interrupted());
        check(tracker.interrupt().energyWh() == 0);
        System.out.println("Testes do histórico passaram");
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError();
    }
}
