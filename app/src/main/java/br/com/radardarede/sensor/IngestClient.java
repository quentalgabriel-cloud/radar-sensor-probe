package br.com.radardarede.sensor;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class IngestClient {
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private final SensorConfig config;

    public IngestClient(SensorConfig config) { this.config = config; }

    public Result post(String path, String body) {
        HttpURLConnection connection = null;
        try {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            connection = (HttpURLConnection) new URL(config.endpoint + path).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("content-type", "application/json; charset=utf-8");
            connection.setRequestProperty("authorization", "Bearer " + config.deviceSecret);
            connection.setRequestProperty("user-agent", "RadarSensor/" + BuildConfig.VERSION_NAME);
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
            }
            int status = connection.getResponseCode();
            consume(status >= 200 && status < 400 ? connection.getInputStream() : connection.getErrorStream());
            return status >= 200 && status < 300
                    ? Result.success(status)
                    : Result.failure("HTTP_" + status);
        } catch (Exception error) {
            return Result.failure(error.getClass().getSimpleName());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void consume(InputStream stream) {
        if (stream == null) return;
        try (InputStream input = stream) {
            byte[] buffer = new byte[1024];
            while (input.read(buffer) != -1) { }
        } catch (Exception ignored) { }
    }

    public static final class Result {
        public final boolean successful;
        public final int status;
        public final String error;

        private Result(boolean successful, int status, String error) {
            this.successful = successful;
            this.status = status;
            this.error = error;
        }

        static Result success(int status) { return new Result(true, status, null); }
        static Result failure(String error) { return new Result(false, 0, error); }
    }
}
