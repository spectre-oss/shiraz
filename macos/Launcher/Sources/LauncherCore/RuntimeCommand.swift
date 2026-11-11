// SPDX-License-Identifier: GPL-3.0-only
import Foundation

/// Builds `shiraz` command-line arguments from launcher state.
public enum RuntimeCommand {
    public static func inspect(apk: URL) -> [String] {
        ["inspect", apk.path]
    }

    public static func label(apk: URL) -> [String] {
        ["label", apk.path]
    }

    public static func icon(apk: URL, outputDirectory: URL) -> [String] {
        ["icon", apk.path, "--output-dir", outputDirectory.path]
    }

    public static func run(apk: URL, settings: AppSettings) -> [String] {
        var arguments = [
            "run", apk.path,
            "--width", String(settings.width),
            "--height", String(settings.height),
            "--log-level", settings.logLevel.rawValue,
        ]
        if let timeout = settings.timeoutSeconds {
            arguments += ["--timeout", String(timeout)]
        }
        return arguments
    }
}
