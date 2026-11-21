// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike;

import android.content.Context;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public final class IkeSession implements AutoCloseable {
    private final IkeSessionParams mIkeSessionParams;
    private final Executor mCallbackExecutor;
    private final IkeSessionCallback mIkeSessionCallback;
    private final ChildSessionCallback mFirstChildCallback;
    private final AtomicBoolean mClosed = new AtomicBoolean(false);

    public IkeSession(
            Context context,
            IkeSessionParams ikeSessionParams,
            ChildSessionParams firstChildSessionParams,
            Executor userCbExecutor,
            IkeSessionCallback ikeSessionCallback,
            ChildSessionCallback firstChildSessionCallback) {
        Objects.requireNonNull(context, "context");
        this.mIkeSessionParams = Objects.requireNonNull(ikeSessionParams, "ikeSessionParams");
        Objects.requireNonNull(firstChildSessionParams, "firstChildSessionParams");
        this.mCallbackExecutor = Objects.requireNonNull(userCbExecutor, "userCbExecutor");
        this.mIkeSessionCallback =
                Objects.requireNonNull(ikeSessionCallback, "ikeSessionCallback");
        this.mFirstChildCallback =
                Objects.requireNonNull(firstChildSessionCallback, "firstChildSessionCallback");
    }

    @Override
    @SuppressWarnings("deprecation")
    public void finalize() {
        kill();
    }

    public void openChildSession(
            ChildSessionParams childSessionParams, ChildSessionCallback childSessionCallback) {
        Objects.requireNonNull(childSessionParams, "childSessionParams");
        Objects.requireNonNull(childSessionCallback, "childSessionCallback");
    }

    public void closeChildSession(final ChildSessionCallback childSessionCallback) {
        Objects.requireNonNull(childSessionCallback, "childSessionCallback");
        mCallbackExecutor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        childSessionCallback.onClosed();
                    }
                });
    }

    @Override
    public void close() {
        kill();
    }

    public void kill() {
        if (mClosed.compareAndSet(false, true)) {
            mCallbackExecutor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            mFirstChildCallback.onClosed();
                            mIkeSessionCallback.onClosed();
                        }
                    });
        }
    }

    public void dump(PrintWriter pw) {
        Objects.requireNonNull(pw, "pw");
        pw.println("IkeSession[server=" + mIkeSessionParams.getServerHostname() + ", closed=" + mClosed.get() + "]");
    }
}
