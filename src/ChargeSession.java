package br.com.memotos.bateria;

final class ChargeSession {
    private static final long MAX_GAP_MS = 10_000;
    private static final long STOP_CONFIRM_MS = 6_000;
    private long start = -1, last, stopped = -1;
    private double previousW, peakW, energyWh;

    record Summary(long end, long durationMs, double peakW, double energyWh, boolean interrupted) {}

    Summary sample(long time, double volts, double amps) {
        double watts = Math.max(0, volts * amps);
        Summary result = null;
        if (start >= 0 && time - last > MAX_GAP_MS) result = interrupt();
        if (start < 0) {
            if (watts == 0) return result;
            start = time;
        } else {
            energyWh += (previousW + watts) * (time - last) / 7_200_000.0;
        }
        last = time;
        previousW = watts;
        peakW = Math.max(peakW, watts);
        if (watts > 0) stopped = -1;
        else if (stopped < 0) stopped = time;
        else if (time - stopped >= STOP_CONFIRM_MS) return finish(stopped, false);
        return result;
    }

    Summary interrupt() {
        return start < 0 ? null : finish(last, true);
    }

    private Summary finish(long end, boolean interrupted) {
        var summary = new Summary(end, end - start, peakW, energyWh, interrupted);
        start = -1;
        stopped = -1;
        previousW = peakW = energyWh = 0;
        return summary;
    }
}
