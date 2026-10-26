// SPDX-License-Identifier: GPL-3.0-only
package android.os;
public final class Message implements Parcelable {
    public int what, arg1, arg2;
    public Object obj;
    Handler target;
    Runnable callback;
    Bundle data;
    long when;
    Message next;
    public Messenger replyTo;
    private boolean used, recycled, asynchronous;
    public Message() { }
    public static Message obtain() { return new Message(); }
    public static Message obtain(Message source) {
        Message copy = obtain(); copy.copyFrom(source); copy.target = source.target; copy.callback = source.callback; return copy;
    }
    public static Message obtain(Handler target) { Message message = obtain(); message.target = target; return message; }
    public static Message obtain(Handler target, Runnable callback) { Message message = obtain(target); message.callback = callback; return message; }
    public static Message obtain(Handler target, int what) { Message message = obtain(target); message.what = what; return message; }
    public static Message obtain(Handler target, int what, Object object) { Message message = obtain(target, what); message.obj = object; return message; }
    public static Message obtain(Handler target, int what, int arg1, int arg2) { return obtain(target, what, arg1, arg2, null); }
    public static Message obtain(Handler target, int what, int arg1, int arg2, Object object) {
        Message message = obtain(target, what, object); message.arg1 = arg1; message.arg2 = arg2; return message;
    }
    public void copyFrom(Message source) {
        what = source.what; arg1 = source.arg1; arg2 = source.arg2; obj = source.obj;
        data = source.data == null ? null : new Bundle(source.data); asynchronous = source.asynchronous;
    }
    public Handler getTarget() { return target; }
    public void setTarget(Handler target) { this.target = target; }
    public Runnable getCallback() { return callback; }
    public long getWhen() { return when; }
    public Bundle getData() { if (data == null) data = new Bundle(); return data; }
    public Bundle peekData() { return data; }
    public void setData(Bundle data) { this.data = data; }
    public boolean isAsynchronous() { return asynchronous; }
    public void setAsynchronous(boolean value) { asynchronous = value; }
    public void sendToTarget() { if (target == null) throw new IllegalStateException("Message has no target"); target.sendMessage(this); }
    synchronized void markInUse() {
        if (used || recycled) throw new IllegalStateException("Message is already in use or recycled");
        used = true;
    }
    public synchronized void recycle() {
        if (used) throw new IllegalStateException("Cannot recycle a queued/dispatching message");
        recycleUnchecked();
    }
    synchronized void recycleUnchecked() {
        used = false; recycled = true; target = null; callback = null; data = null; obj = null; next = null;
    }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel destination, int flags) { throw new UnsupportedOperationException("Message parcel transport is not implemented"); }
}
