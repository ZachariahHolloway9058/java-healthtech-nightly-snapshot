package healthtech;

public final class OperationalNotification {
    private OperationalNotification() {}

    public static String message(int appointmentCount, int cancelledCount, String objectKey) {
        return "Nightly snapshot stored: " + appointmentCount + " appointments; "
                + cancelledCount + " cancelled; object=" + objectKey;
    }
}
