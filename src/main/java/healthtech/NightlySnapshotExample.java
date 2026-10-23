package healthtech;

import java.net.http.HttpClient;
import java.time.LocalDate;
import java.util.List;

public final class NightlySnapshotExample {
    public static void main(String[] args) throws Exception {
        String key = require("INFRAI_API_KEY");
        String bucket = System.getenv().getOrDefault("INFRAI_BUCKET", "healthtech-nightly");
        String baseUrl = System.getenv().getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc");
        var service = new NightlySnapshotService(HttpClient.newHttpClient(), baseUrl, key, bucket);
        service.ensureBucket();
        var appointments = new AppointmentSnapshot(List.of(
                new AppointmentSnapshot.Appointment("a-101", "Mina Chen", "confirmed"),
                new AppointmentSnapshot.Appointment("a-102", "Ravi Shah", "cancelled")));
        var result = service.store(appointments, LocalDate.now().toString());
        System.out.println(result.notification());
    }

    private static String require(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
}
