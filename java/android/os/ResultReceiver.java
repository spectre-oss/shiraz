// SPDX-License-Identifier: GPL-3.0-only
package android.os;

/** In-process result delivery. Remote Binder transport is not available. */
public class ResultReceiver implements Parcelable {
    private final Handler handler;
    public ResultReceiver(Handler handler) { this.handler=handler; }
    public void send(final int code,final Bundle data) {
        if(handler==null)onReceiveResult(code,data);
        else handler.post(new Runnable(){public void run(){onReceiveResult(code,data);}});
    }
    protected void onReceiveResult(int code,Bundle data) {}
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel destination,int flags) {
        throw new UnsupportedOperationException("ResultReceiver Binder transport is not implemented");
    }
    public static final Parcelable.Creator<ResultReceiver> CREATOR=new Parcelable.Creator<ResultReceiver>() {
        public ResultReceiver createFromParcel(Parcel source) { throw new UnsupportedOperationException("ResultReceiver Binder transport is not implemented"); }
        public ResultReceiver[] newArray(int size) { return new ResultReceiver[size]; }
    };
}
