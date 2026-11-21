// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.syncdispatchfixture;
/** Account rows are seeded only by the trusted fixture harness, never an authenticator UI. */
public final class AuthService extends android.app.Service {
 public android.os.IBinder onBind(android.content.Intent intent){throw new UnsupportedOperationException("Fixture has no interactive authentication");}
}
