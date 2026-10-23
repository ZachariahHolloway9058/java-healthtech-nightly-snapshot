package healthtech;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;

public final class NightlySnapshotService {
    private final HttpClient http;
    private final String baseUrl;
    private final String apiKey;
    private final String bucket;

    public NightlySnapshotService(HttpClient http, String baseUrl, String apiKey, String bucket) {
        this.http = Objects.requireNonNull(http);
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.bucket = bucket;
    }

    public void ensureBucket() throws IOException, InterruptedException {
        call("POST", "/v1/storage/bucket/create", "{\"name\":\"" + json(bucket) + "\"}");
    }

    public SnapshotResult store(AppointmentSnapshot input, String date) throws IOException, InterruptedException {
        int cancelled = (int) input.appointments().stream().filter(a -> "cancelled".equals(a.status())).count();
        String key = "nightly/appointments-" + date + ".json";
        String payload = jsonPayload(input);
        String body = "{\"data_base64\":\"" + Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "\",\"content_type\":\"application/json\",\"idempotency_key\":\"snapshot-" + json(date) + "\"}";
        // Canonical capability: infrai.storage.object.put
        call("PUT", "/v1/storage/object/put/" + encode(bucket) + "/" + encode(key), body);
        return new SnapshotResult(key, input.appointments().size(), cancelled,
                OperationalNotification.message(input.appointments().size(), cancelled, key));
    }

    private String jsonPayload(AppointmentSnapshot input) {
        StringBuilder out = new StringBuilder("{\"appointments\":[");
        for (int i = 0; i < input.appointments().size(); i++) {
            var a = input.appointments().get(i);
            if (i > 0) out.append(',');
            out.append("{\"id\":\"").append(json(a.id()).replace("\"", "\\\""))
                    .append("\",\"patientName\":\"").append(json(a.patientName()).replace("\"", "\\\""))
                    .append("\",\"status\":\"").append(json(a.status()).replace("\"", "\\\""))
                    .append("\"}");
        }
        return out.append("]}").toString();
    }

    private String call(String method, String path, String body) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String envelope = response.body();
            if (!envelope.contains("\"ok\":true")) throw new IOException("Infrai request rejected: " + envelope);
            if (response.statusCode() != 429) return envelope;
            long wait = response.headers().firstValue("Retry-After").map(Long::parseLong).orElse(1L << attempt);
            Thread.sleep(wait * 1000L);
        }
        throw new IOException("request retry limit reached");
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String json(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }

    public record SnapshotResult(String objectKey, int appointmentCount, int cancelledCount, String notification) {}
}
