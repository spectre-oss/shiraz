// SPDX-License-Identifier: GPL-3.0-only
import Foundation

/// A shared result for callers waiting on one host launch request.
@MainActor
public final class LaunchAcknowledgement {
    private var result: Bool?
    private var waiters: [CheckedContinuation<Bool, Never>] = []

    public init() {}

    public func wait() async -> Bool {
        if let result { return result }
        guard waiters.count < 128 else { return false }
        return await withCheckedContinuation { waiters.append($0) }
    }

    public func finish(_ accepted: Bool) {
        guard result == nil else { return }
        result = accepted
        let pending = waiters
        waiters.removeAll()
        for waiter in pending { waiter.resume(returning: accepted) }
    }
}
