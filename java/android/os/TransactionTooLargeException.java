// SPDX-License-Identifier: GPL-3.0-only
package android.os;

public class TransactionTooLargeException extends RemoteException {
    public TransactionTooLargeException() {
        super();
    }

    public TransactionTooLargeException(String msg) {
        super(msg);
    }
}
