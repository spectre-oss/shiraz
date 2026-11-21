// SPDX-License-Identifier: GPL-3.0-only
package android.content;
/** A manifest receiver attempted an operation requiring a longer-lived context. */
public class ReceiverCallNotAllowedException extends android.util.AndroidRuntimeException {
 public ReceiverCallNotAllowedException(String message){super(message);}
}
