// SPDX-License-Identifier: GPL-3.0-only
package android.service.credentials;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Candidate entries and actions returned from {@link CredentialProviderService#onBeginGetCredential}. */
public final class BeginGetCredentialResponse implements Parcelable {
    private final List<CredentialEntry> mCredentialEntries;
    private final List<Action> mAuthenticationActions;
    private final List<Action> mActions;
    private final RemoteEntry mRemoteCredentialEntry;

    public BeginGetCredentialResponse() {
        this(
                Collections.<CredentialEntry>emptyList(),
                Collections.<Action>emptyList(),
                Collections.<Action>emptyList(),
                null);
    }

    private BeginGetCredentialResponse(
            List<CredentialEntry> credentialEntries,
            List<Action> authenticationActions,
            List<Action> actions,
            RemoteEntry remoteCredentialEntry) {
        this.mCredentialEntries =
                Collections.unmodifiableList(new ArrayList<CredentialEntry>(credentialEntries));
        this.mAuthenticationActions =
                Collections.unmodifiableList(new ArrayList<Action>(authenticationActions));
        this.mActions = Collections.unmodifiableList(new ArrayList<Action>(actions));
        this.mRemoteCredentialEntry = remoteCredentialEntry;
    }

    private BeginGetCredentialResponse(Parcel in) {
        ArrayList<CredentialEntry> cred = in.createTypedArrayList(CredentialEntry.CREATOR);
        this.mCredentialEntries =
                Collections.unmodifiableList(
                        cred != null ? cred : new ArrayList<CredentialEntry>());
        ArrayList<Action> auth = in.createTypedArrayList(Action.CREATOR);
        this.mAuthenticationActions =
                Collections.unmodifiableList(auth != null ? auth : new ArrayList<Action>());
        ArrayList<Action> act = in.createTypedArrayList(Action.CREATOR);
        this.mActions = Collections.unmodifiableList(act != null ? act : new ArrayList<Action>());
        this.mRemoteCredentialEntry = in.readTypedObject(RemoteEntry.CREATOR);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(mCredentialEntries);
        dest.writeTypedList(mAuthenticationActions);
        dest.writeTypedList(mActions);
        dest.writeTypedObject(mRemoteCredentialEntry, flags);
    }

    public List<CredentialEntry> getCredentialEntries() {
        return mCredentialEntries;
    }

    public List<Action> getAuthenticationActions() {
        return mAuthenticationActions;
    }

    public List<Action> getActions() {
        return mActions;
    }

    public RemoteEntry getRemoteCredentialEntry() {
        return mRemoteCredentialEntry;
    }

    public static final class Builder {
        private List<CredentialEntry> mCredentialEntries = new ArrayList<CredentialEntry>();
        private List<Action> mAuthenticationActions = new ArrayList<Action>();
        private List<Action> mActions = new ArrayList<Action>();
        private RemoteEntry mRemoteCredentialEntry;

        public Builder() {
        }

        public Builder setRemoteCredentialEntry(RemoteEntry remoteCredentialEntry) {
            this.mRemoteCredentialEntry = remoteCredentialEntry;
            return this;
        }

        public Builder addCredentialEntry(CredentialEntry credentialEntry) {
            this.mCredentialEntries.add(
                    Objects.requireNonNull(credentialEntry, "credentialEntry must not be null"));
            return this;
        }

        public Builder addAuthenticationAction(Action authenticationAction) {
            this.mAuthenticationActions.add(
                    Objects.requireNonNull(
                            authenticationAction, "authenticationAction must not be null"));
            return this;
        }

        public Builder addAction(Action action) {
            this.mActions.add(Objects.requireNonNull(action, "action must not be null"));
            return this;
        }

        public Builder setActions(List<Action> actions) {
            Objects.requireNonNull(actions, "actions must not be null");
            ArrayList<Action> copy = new ArrayList<Action>(actions.size());
            for (Action a : actions) {
                copy.add(Objects.requireNonNull(a, "action must not be null"));
            }
            this.mActions = copy;
            return this;
        }

        public Builder setCredentialEntries(List<CredentialEntry> credentialEntries) {
            Objects.requireNonNull(credentialEntries, "credentialEntries must not be null");
            ArrayList<CredentialEntry> copy =
                    new ArrayList<CredentialEntry>(credentialEntries.size());
            for (CredentialEntry c : credentialEntries) {
                copy.add(Objects.requireNonNull(c, "credentialEntry must not be null"));
            }
            this.mCredentialEntries = copy;
            return this;
        }

        public Builder setAuthenticationActions(List<Action> authenticationActions) {
            Objects.requireNonNull(
                    authenticationActions, "authenticationActions must not be null");
            ArrayList<Action> copy = new ArrayList<Action>(authenticationActions.size());
            for (Action a : authenticationActions) {
                copy.add(Objects.requireNonNull(a, "authenticationAction must not be null"));
            }
            this.mAuthenticationActions = copy;
            return this;
        }

        public BeginGetCredentialResponse build() {
            return new BeginGetCredentialResponse(
                    mCredentialEntries,
                    mAuthenticationActions,
                    mActions,
                    mRemoteCredentialEntry);
        }
    }

    public static final Parcelable.Creator<BeginGetCredentialResponse> CREATOR =
            new Parcelable.Creator<BeginGetCredentialResponse>() {
                @Override
                public BeginGetCredentialResponse createFromParcel(Parcel in) {
                    return new BeginGetCredentialResponse(in);
                }

                @Override
                public BeginGetCredentialResponse[] newArray(int size) {
                    return new BeginGetCredentialResponse[size];
                }
            };
}
