package br.com.radardarede.sensor;

public final class BackoffPolicy {
    private static final long BASE_MS = 15_000L;
    private static final long MAX_MS = 15L * 60L * 1000L;

    private BackoffPolicy() { }

    public static long delayMs(int previousAttempts) {
        int exponent = Math.max(0, Math.min(previousAttempts, 6));
        return Math.min(MAX_MS, BASE_MS * (1L << exponent));
    }
}
