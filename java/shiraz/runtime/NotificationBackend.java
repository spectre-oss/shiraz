// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
/** Host notification transport, independent of channel policy and foreground ownership. */
public interface NotificationBackend extends java.io.Closeable {
 String[] capabilities();
 long post(long replaces,String summary,String body,int urgency,int timeout,boolean suppressSound);
 void cancel(long id);
 void close();
}
