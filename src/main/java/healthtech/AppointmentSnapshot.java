package healthtech;

import java.util.List;

public record AppointmentSnapshot(List<Appointment> appointments) {
    public record Appointment(String id, String patientName, String status) {}
}
