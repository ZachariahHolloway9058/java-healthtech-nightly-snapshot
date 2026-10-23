package healthtech;

import java.net.http.HttpClient;
import java.util.List;

public final class NightlySnapshotServiceTest {
    public static void main(String[] args) {
        var input = new AppointmentSnapshot(List.of(
                new AppointmentSnapshot.Appointment("a-1", "A", "confirmed"),
                new AppointmentSnapshot.Appointment("a-2", "B", "cancelled")));
        var message = OperationalNotification.message(input.appointments().size(), 1, "nightly/appointments-2026-10-23.json");
        check(message.contains("2 appointments"), "count");
        check(message.contains("1 cancelled"), "cancelled count");
        check(message.contains("nightly/appointments-2026-10-23.json"), "object key");
        check(!message.contains("A") && !message.contains("B"), "patient-safe notice");
        System.out.println("NightlySnapshotServiceTest passed");
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}
