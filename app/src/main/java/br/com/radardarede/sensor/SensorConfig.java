package br.com.radardarede.sensor;

import java.util.regex.Pattern;

public final class SensorConfig {
    private static final Pattern UUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    public final String endpoint;
    public final String networkId;
    public final String deviceId;
    public final String deviceSecret;

    private SensorConfig(String endpoint, String networkId, String deviceId, String deviceSecret) {
        this.endpoint = trimSlash(endpoint);
        this.networkId = networkId;
        this.deviceId = deviceId;
        this.deviceSecret = deviceSecret;
    }

    public static SensorConfig current() {
        return new SensorConfig(BuildConfig.RADAR_INGEST_ENDPOINT, BuildConfig.RADAR_NETWORK_ID,
                BuildConfig.RADAR_DEVICE_ID, BuildConfig.RADAR_DEVICE_SECRET);
    }

    static SensorConfig of(String endpoint, String networkId, String deviceId, String deviceSecret) {
        return new SensorConfig(endpoint, networkId, deviceId, deviceSecret);
    }

    public boolean isProvisioned() {
        return endpoint.startsWith("https://") && endpoint.length() <= 500
                && UUID.matcher(networkId).matches()
                && UUID.matcher(deviceId).matches()
                && deviceSecret != null && deviceSecret.length() >= 32;
    }

    private static String trimSlash(String value) {
        if (value == null) return "";
        String result = value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }
}
