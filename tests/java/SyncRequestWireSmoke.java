// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.os.Bundle;
import android.accounts.Account;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class SyncRequestWireSmoke {
    public static void main(String[] args) throws Exception {
        Bundle extras = new Bundle();
        extras.putLong("large", Long.MAX_VALUE);
        extras.putInt("small", 3);
        extras.putBoolean("force", true);
        extras.putParcelable("account", new Account("fixture", "test.accounts"));
        extras.putString("null", null);
        String json = new String(SyncRequests.encode(null, "test.data", extras), StandardCharsets.UTF_8);
        if (!json.contains("9223372036854775807") || !json.contains("\"type\":\"long\"") || !json.contains("\"account\":null"))
            throw new AssertionError("Typed request serialization");
        if (SyncRequests.decode("{\"ok\":true,\"queued\":0}".getBytes(StandardCharsets.UTF_8)) != 0) throw new AssertionError();
        for (String invalid : new String[]{"{\"ok\":true}", "{\"ok\":true,\"queued\":1.5}", "{\"ok\":true,\"queued\":129}", "{\"ok\":true,\"queued\":0,\"queued\":0}"}) {
            try { SyncRequests.decode(invalid.getBytes(StandardCharsets.UTF_8)); throw new AssertionError("Malformed reply accepted"); }
            catch (IOException expected) {}
        }
        try { SyncRequests.decode("{\"ok\":false,\"error\":\"denied\"}".getBytes(StandardCharsets.UTF_8)); throw new AssertionError(); }
        catch (SecurityException expected) {}
        extras.putDouble("invalid", Double.NaN);
        try { SyncRequests.encode(null, null, extras); throw new AssertionError("NaN accepted"); }
        catch (IllegalArgumentException expected) {}
        System.out.println("SYNC_REQUEST_WIRE_OK");
    }
}
