// SPDX-License-Identifier: GPL-3.0-only
package android.os;

/** Nominal API-27 compatibility profile, not a claim of complete Android API coverage. */
public final class Build {
    public static final String UNKNOWN = "unknown";
    public static final String MODEL = shiraz.runtime.DeviceProfile.get("build.model", "Shiraz Linux"), MANUFACTURER = shiraz.runtime.DeviceProfile.get("build.manufacturer", "Shiraz"), BRAND = shiraz.runtime.DeviceProfile.get("build.brand", "Shiraz");
    public static final String DEVICE = shiraz.runtime.DeviceProfile.get("build.device", "linux"), PRODUCT = shiraz.runtime.DeviceProfile.get("build.product", "shiraz"), HARDWARE = shiraz.runtime.DeviceProfile.get("build.hardware", System.getProperty("os.arch", "unknown"));
    public static final String BOARD = shiraz.runtime.DeviceProfile.get("build.board", "linux"), BOOTLOADER = shiraz.runtime.DeviceProfile.get("build.bootloader", UNKNOWN), RADIO = UNKNOWN, SERIAL = shiraz.runtime.DeviceProfile.get("build.serial", UNKNOWN);
    public static String getSerial() { shiraz.runtime.HostAppInstaller.recordAccess("build:getSerial"); return SERIAL; }
    // No Android SoC properties are supplied by the Linux host profile.
    public static final String SOC_MANUFACTURER = UNKNOWN, SOC_MODEL = UNKNOWN;
    public static final String HOST = shiraz.runtime.DeviceProfile.get("build.host", "shiraz"), USER = shiraz.runtime.DeviceProfile.get("build.user", "shiraz");
    public static final long TIME = shiraz.runtime.DeviceProfile.longValue("build.time",0L);
    // Identifies this unsigned Linux runtime build, without host/user identity or signing claims.
    public static final String ID = shiraz.runtime.DeviceProfile.get("build.id", "shiraz"), TYPE = shiraz.runtime.DeviceProfile.get("build.type", "host"), TAGS = shiraz.runtime.DeviceProfile.get("build.tags", "unsigned");
    public static final String DISPLAY = shiraz.runtime.DeviceProfile.get("build.display", "Shiraz " + shiraz.runtime.RuntimeVersion.get());
    public static final String FINGERPRINT = BRAND + "/" + PRODUCT + "/" + DEVICE + ":" + VERSION.RELEASE
        + "/" + ID + "/" + VERSION.INCREMENTAL + ":" + TYPE + "/" + TAGS;
    public static final String CPU_ABI = System.getProperty("shiraz.guestAbi", shiraz.runtime.DeviceProfile.get("build.abi", ("aarch64".equals(HARDWARE) || "arm64".equals(HARDWARE)) ? "arm64-v8a" : "x86_64")), CPU_ABI2 = "";
    public static final String[] SUPPORTED_ABIS = {CPU_ABI};
    public static final String[] SUPPORTED_64_BIT_ABIS = {CPU_ABI}, SUPPORTED_32_BIT_ABIS = {};
    private Build() { }
    public static final class VERSION {
        // Method initialization keeps SDK_INT non-constant, as in Android's SDK.
        public static final int SDK_INT = profileApi();
        public static final String INCREMENTAL = shiraz.runtime.DeviceProfile.get("build.incremental", shiraz.runtime.RuntimeVersion.get());
        public static final String SDK = Integer.toString(SDK_INT);
        public static final String RELEASE = shiraz.runtime.DeviceProfile.get("build.release", "8.1"), CODENAME = "REL", SECURITY_PATCH = shiraz.runtime.DeviceProfile.get("build.securityPatch", UNKNOWN), BASE_OS = "";
        public static final int PREVIEW_SDK_INT = 0;
        private static int profileApi() { return 27; }
        private VERSION() { }
    }
    public static final class VERSION_CODES {
        public static final int HONEYCOMB = 11;
        public static final int LOLLIPOP = 21, LOLLIPOP_MR1 = 22, M = 23, N = 24, N_MR1 = 25;
        public static final int O = 26, O_MR1 = 27, P = 28, Q = 29, R = 30, S = 31, S_V2 = 32;
        public static final int TIRAMISU = 33, UPSIDE_DOWN_CAKE = 34, VANILLA_ICE_CREAM = 35;
        private VERSION_CODES() { }
    }
}
