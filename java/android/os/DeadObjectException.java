// SPDX-License-Identifier: GPL-3.0-only
package android.os;
public class DeadObjectException extends RemoteException {
 public DeadObjectException() { }
 public DeadObjectException(String message) { super(message); }
}
