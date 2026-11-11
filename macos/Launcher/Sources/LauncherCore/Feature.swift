// SPDX-License-Identifier: GPL-3.0-only
import Foundation

/// Launcher features that have a place in the UI but no implementation yet.
/// Every control for one of these calls `perform()`, which throws
/// `NotImplementedError`; the app shows that error and changes nothing.
/// When a feature lands, delete its case and wire the control to real code.
public enum Feature: String, CaseIterable, Sendable, Identifiable {
    // Library
    case collections
    case catalog
    case updates
    case autoUpdates
    // Per-app actions (docs/macos_final_goal.md: desktop integration, bundle builder)
    case addToApplications
    case exportMacApp
    case checkForUpdate
    case duplicateApp
    case replaceAPK
    // App storage (Application Support/Shiraz/<package>)
    case revealAppData
    case clearAppData
    case clearAppCache
    case backupAppData
    // Permissions and policy
    case grantPermission
    case networkAccess
    case notifications
    // Display and input
    case displayScale
    case fullScreen
    case renderer
    case keyboardMapping
    case gameController
    case language
    // Runtime
    case runtimeUpdate
    case diagnosticsReport
    case performanceStats
    case keepAwake
    case copyToClipboard

    public var id: String { rawValue }

    public var title: String {
        switch self {
        case .collections: "Collections"
        case .catalog: "F-Droid Catalog"
        case .updates: "Updates"
        case .autoUpdates: "Automatic Updates"
        case .addToApplications: "Add to Applications"
        case .exportMacApp: "Export as Mac App"
        case .checkForUpdate: "Check for Update"
        case .duplicateApp: "Duplicate"
        case .replaceAPK: "Replace APK"
        case .revealAppData: "Show App Data"
        case .clearAppData: "Clear App Data"
        case .clearAppCache: "Clear Cache"
        case .backupAppData: "Back Up App Data"
        case .grantPermission: "Permission Control"
        case .networkAccess: "Network Access"
        case .notifications: "Notifications"
        case .displayScale: "Display Scale"
        case .fullScreen: "Full Screen"
        case .renderer: "Renderer"
        case .keyboardMapping: "Keyboard Mapping"
        case .gameController: "Game Controllers"
        case .language: "App Language"
        case .runtimeUpdate: "Runtime Update"
        case .diagnosticsReport: "Diagnostics Report"
        case .performanceStats: "Performance"
        case .keepAwake: "Keep Awake"
        case .copyToClipboard: "Copy"
        }
    }

    public func perform() throws(NotImplementedError) {
        throw NotImplementedError(feature: self)
    }
}

public struct NotImplementedError: LocalizedError, Equatable, Sendable {
    public var feature: Feature
    public var errorDescription: String? { "\(feature.title) is not implemented." }
}
