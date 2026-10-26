// SPDX-License-Identifier: GPL-3.0-only
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import shiraz.runtime.HostSensorManager;
public final class SensorDiscoverySmoke {
    interface Attempt { void run() throws Exception; }
    static void unavailable(Path root) {
        HostSensorManager manager = new HostSensorManager(root.toFile());
        if (!manager.getSensorList(-1).isEmpty() || manager.getDefaultSensor(1) != null)
            throw new AssertionError("Unbridged hardware advertised as supported");
        if (manager.registerListener(null, null, 0) || manager.flush(null))
            throw new AssertionError("Unbridged hardware accepted subscription");
    }
    static void invalid(Attempt task) throws Exception {
        try { task.run(); } catch (IllegalStateException expected) { return; }
        throw new AssertionError("Unverifiable discovery was reported as empty");
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Paths.get(args[0]), "discovery-");
        try {
            invalid(() -> new HostSensorManager(root.toFile()));
            Path input = Files.createDirectories(root.resolve("class/input/input0"));
            Path properties = input.resolve("properties");
            Files.write(properties, "0\n".getBytes(StandardCharsets.US_ASCII));
            if (!new HostSensorManager(root.toFile()).getSensorList(-1).isEmpty()) throw new AssertionError("Empty Linux inventory");
            Files.write(properties, "1 40\n".getBytes(StandardCharsets.US_ASCII)); unavailable(root);
            Files.write(properties, "invalid\n".getBytes(StandardCharsets.US_ASCII)); invalid(() -> new HostSensorManager(root.toFile()));
            Files.write(properties, new byte[4097]); invalid(() -> new HostSensorManager(root.toFile()));
            Files.write(properties, "0\n".getBytes(StandardCharsets.US_ASCII));
            Files.createDirectories(root.resolve("bus/iio/devices/iio:device0")); unavailable(root);
            System.out.println("LINUX_SENSOR_DISCOVERY_OK");
        } finally {
            try (java.util.stream.Stream<Path> files = Files.walk(root)) { files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> { try { Files.delete(path); } catch (Exception error) { throw new RuntimeException(error); } }); }
        }
    }
}
