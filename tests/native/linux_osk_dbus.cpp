// SPDX-License-Identifier: GPL-3.0-only
#include "../../native/linux/osk_dbus.inc"
#include <cassert>
#include <cstdlib>
#include <iostream>

int main() {
    using namespace shiraz::linux_osk;

    // 1. Verify bus name validation and object path derivation.
    assert(objectPathForService("org.spectreos.OSK0") == "/org/spectreos/OSK0");
    assert(objectPathForService("sm.puri.OSK0") == "/sm/puri/OSK0");
    assert(isValidBusName("org.spectreos.OSK0"));
    assert(isValidBusName("sm.puri.OSK0"));
    assert(!isValidBusName(""));
    assert(!isValidBusName("no_dot"));
    assert(!isValidBusName(".leading.dot"));
    assert(!isValidBusName("double..dot"));
    assert(!isValidBusName("bad/char.name"));

    // 2. Verify default priority: SpectreOS OSK first, legacy mobile OSK fallback second.
    unsetenv("SHIRAZ_OSK_DBUS_SERVICE");
    auto defaults = defaultTargets();
    assert(defaults.size() == 2);
    assert(defaults[0].service == "org.spectreos.OSK0");
    assert(defaults[0].path == "/org/spectreos/OSK0");
    assert(defaults[0].interface == "org.spectreos.OSK0");
    assert(defaults[1].service == "sm.puri.OSK0");
    assert(defaults[1].path == "/sm/puri/OSK0");

    // 3. Verify explicit override env var.
    setenv("SHIRAZ_OSK_DBUS_SERVICE", "org.example.TestOSK0", 1);
    auto custom = defaultTargets();
    assert(custom.size() == 1);
    assert(custom[0].service == "org.example.TestOSK0");
    assert(custom[0].path == "/org/example/TestOSK0");
    unsetenv("SHIRAZ_OSK_DBUS_SERVICE");

    // 4. Verify SHIRAZ_OSK_DBUS=0 disables session calls cleanly.
    setenv("SHIRAZ_OSK_DBUS", "0", 1);
    assert(!envEnabled());
    {
        Session s;
        s.setVisible(true);
        s.setVisible(false);
    }
    unsetenv("SHIRAZ_OSK_DBUS");
    assert(envEnabled());

    // 5. Verify graceful non-blocking no-op when no session bus is available.
    setenv("DBUS_SESSION_BUS_ADDRESS", "unix:path=/nonexistent/shiraz-osk-test-bus", 1);
    {
        Session s;
        s.setVisible(true);
        s.setVisible(false);
        s.shutdown();
    }

    std::cout << "LINUX_OSK_DBUS_OK\n";
    return 0;
}
