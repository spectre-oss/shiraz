// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.hardware.*;
import android.os.Handler;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/** Enumerates supported Android sensors; detected but unbridged Linux devices are unavailable. */
public final class HostSensorManager extends SensorManager {
    public HostSensorManager() { this(new File("/sys")); }
    public HostSensorManager(File sysRoot) {
        if (DeviceProfile.selected()) return; // The selected virtual device has no bridged sensors.
        try {
            File iio = new File(sysRoot, "bus/iio/devices"), inputs = new File(sysRoot, "class/input");
            if (!iio.isDirectory() && !inputs.isDirectory()) throw new IOException("Linux sensor discovery interfaces are unavailable");
            for (File device : entries(iio)) if (device.getName().startsWith("iio:device"))
                System.err.println("W/ShirazSensors: Linux IIO device detected; sensor event bridge unavailable");
            for (File device : entries(inputs)) {
                File properties = new File(device, "properties");
                if (!properties.exists()) properties = new File(device, "device/properties");
                if (!properties.exists()) continue;
                byte[] bytes = new byte[4097]; int count = 0;
                try (InputStream input = new FileInputStream(properties)) {
                    int size; while (count < bytes.length && (size = input.read(bytes, count, bytes.length - count)) != -1) count += size;
                }
                if (count == 0 || count == bytes.length) throw new IOException("Invalid Linux input property bitmap size");
                String[] words = new String(bytes, 0, count, StandardCharsets.US_ASCII).trim().split("\\s+");
                long low;
                try { low = Long.parseUnsignedLong(words[words.length - 1], 16); }
                catch (NumberFormatException invalid) { throw new IOException("Invalid Linux input property bitmap", invalid); }
                if ((low & 0x40) != 0) System.err.println("W/ShirazSensors: Linux input accelerometer detected; sensor event bridge unavailable");
            }
        } catch (IOException error) { throw new IllegalStateException("Cannot determine Linux sensor availability", error); }
    }
    private static File[] entries(File directory) throws IOException {
        if (!directory.exists()) return new File[0];
        File[] entries = directory.listFiles();
        if (entries == null || entries.length > 4096) throw new IOException("Cannot enumerate Linux sensor interfaces");
        return entries;
    }
    public List<Sensor> getSensorList(int type) { return Collections.emptyList(); }
    public boolean registerListener(SensorEventListener listener, Sensor sensor, int delay, int latency, Handler handler) {
        // No Sensor instance is available from this backend. Never report a successful subscription.
        return false;
    }
    public void unregisterListener(SensorEventListener listener) { }
    public void unregisterListener(SensorEventListener listener, Sensor sensor) { }
    public boolean flush(SensorEventListener listener) { return false; }
}
