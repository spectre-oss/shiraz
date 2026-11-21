// SPDX-License-Identifier: GPL-3.0-only
package java.util.concurrent;

/**
 * Interrelated interfaces and static methods for establishing flow-controlled components.
 */
public final class Flow {
    static final int DEFAULT_BUFFER_SIZE = 256;

    private Flow() {
    }

    public static int defaultBufferSize() {
        return DEFAULT_BUFFER_SIZE;
    }

    public interface Publisher<T> {
        void subscribe(Subscriber<? super T> subscriber);
    }

    public interface Subscriber<T> {
        void onSubscribe(Subscription subscription);

        void onNext(T item);

        void onError(Throwable throwable);

        void onComplete();
    }

    public interface Subscription {
        void request(long n);

        void cancel();
    }

    public interface Processor<T, R> extends Subscriber<T>, Publisher<R> {
    }
}
