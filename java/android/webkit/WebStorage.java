package android.webkit;

/** Web storage is not backed by a browser engine; every operation is a no-op. */
public class WebStorage {
    private static final WebStorage INSTANCE = new WebStorage();

    public static WebStorage getInstance() { return INSTANCE; }
    public void deleteAllData() {}
    public void deleteOrigin(String origin) {}
}
