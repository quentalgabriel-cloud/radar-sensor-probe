package br.com.radardarede.sensor;

public final class CaptureTestEvaluator {
    private static final long MESSAGE_TIME_TOLERANCE_MS = 60_000L;

    private CaptureTestEvaluator() { }

    public static boolean shouldPass(boolean waiting, long startedAt, long observedAt,
                                     int messageCount, long latestMessageAt, boolean recovered) {
        if (!waiting || recovered || startedAt <= 0 || observedAt < startedAt || messageCount <= 0) {
            return false;
        }
        return latestMessageAt > 0 && latestMessageAt >= startedAt - MESSAGE_TIME_TOLERANCE_MS;
    }
}
