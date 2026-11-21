// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.measurement;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.InputEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** App source registration request containing multiple registration URIs and optional input event. */
public final class SourceRegistrationRequest implements Parcelable {
    private static final AtomicInteger NEXT_INPUT_EVENT_ID = new AtomicInteger(1);
    private static final Map<Integer, InputEvent> INPUT_EVENTS =
            new ConcurrentHashMap<Integer, InputEvent>();

    private final List<Uri> mRegistrationUris;
    private final InputEvent mInputEvent;

    private SourceRegistrationRequest(List<Uri> registrationUris, InputEvent inputEvent) {
        Objects.requireNonNull(registrationUris, "registrationUris");
        if (registrationUris.isEmpty()) {
            throw new IllegalArgumentException("registrationUris must not be empty");
        }
        List<Uri> copy = new ArrayList<Uri>(registrationUris.size());
        for (Uri uri : registrationUris) {
            copy.add(Objects.requireNonNull(uri, "registrationUri"));
        }
        this.mRegistrationUris = Collections.unmodifiableList(copy);
        this.mInputEvent = inputEvent;
    }

    public List<Uri> getRegistrationUris() {
        return mRegistrationUris;
    }

    public InputEvent getInputEvent() {
        return mInputEvent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SourceRegistrationRequest)) {
            return false;
        }
        SourceRegistrationRequest that = (SourceRegistrationRequest) o;
        return Objects.equals(mRegistrationUris, that.mRegistrationUris)
                && Objects.equals(mInputEvent, that.mInputEvent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mRegistrationUris, mInputEvent);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        Objects.requireNonNull(out, "out");
        out.writeInt(mRegistrationUris.size());
        for (Uri uri : mRegistrationUris) {
            out.writeString(uri.toString());
        }
        writeInputEvent(out, mInputEvent);
    }

    static void writeInputEvent(Parcel out, InputEvent event) {
        if (event == null) {
            out.writeInt(0);
            return;
        }
        int id = NEXT_INPUT_EVENT_ID.getAndIncrement();
        if (INPUT_EVENTS.size() >= 1024) {
            INPUT_EVENTS.clear();
        }
        INPUT_EVENTS.put(Integer.valueOf(id), event);
        out.writeInt(id);
    }

    static InputEvent readInputEvent(Parcel in) {
        int id = in.readInt();
        return id == 0 ? null : INPUT_EVENTS.get(Integer.valueOf(id));
    }

    public static final class Builder {
        private final List<Uri> mRegistrationUris;
        private InputEvent mInputEvent;

        public Builder(List<Uri> registrationUris) {
            Objects.requireNonNull(registrationUris, "registrationUris");
            this.mRegistrationUris = new ArrayList<Uri>(registrationUris);
        }

        public Builder setInputEvent(InputEvent inputEvent) {
            this.mInputEvent = inputEvent;
            return this;
        }

        public SourceRegistrationRequest build() {
            return new SourceRegistrationRequest(mRegistrationUris, mInputEvent);
        }
    }

    public static final Parcelable.Creator<SourceRegistrationRequest> CREATOR =
            new Parcelable.Creator<SourceRegistrationRequest>() {
                @Override
                public SourceRegistrationRequest createFromParcel(Parcel in) {
                    Objects.requireNonNull(in, "in");
                    int count = in.readInt();
                    List<Uri> uris = new ArrayList<Uri>(count);
                    for (int i = 0; i < count; i++) {
                        uris.add(Uri.parse(in.readString()));
                    }
                    InputEvent event = readInputEvent(in);
                    return new SourceRegistrationRequest(uris, event);
                }

                @Override
                public SourceRegistrationRequest[] newArray(int size) {
                    return new SourceRegistrationRequest[size];
                }
            };
}
