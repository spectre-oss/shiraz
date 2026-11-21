// SPDX-License-Identifier: GPL-3.0-only
import Testing
@testable import LauncherCore

@MainActor
@Suite struct LaunchAcknowledgementTests {
    @Test(arguments: [true, false])
    func waitsForHostResultAndSharesIt(_ accepted: Bool) async {
        let receipt = LaunchAcknowledgement()
        var delivered = 0
        var tasks: [Task<Bool, Never>] = []
        for _ in 0 ..< 2 {
            await withCheckedContinuation { ready in
                tasks.append(Task { @MainActor in
                    ready.resume()
                    let result = await receipt.wait()
                    delivered += 1
                    return result
                })
            }
        }
        #expect(delivered == 0)
        receipt.finish(accepted)
        receipt.finish(!accepted) // A later cleanup cannot rewrite an acknowledgment.
        for task in tasks { #expect(await task.value == accepted) }
        #expect(delivered == 2)
        #expect(await receipt.wait() == accepted)
    }
}
