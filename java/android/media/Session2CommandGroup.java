// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class Session2CommandGroup implements Parcelable {
    private final Set<Session2Command> mCommands;

    Session2CommandGroup(Collection<Session2Command> commands) {
        mCommands = commands != null
                ? new LinkedHashSet<Session2Command>(commands)
                : new LinkedHashSet<Session2Command>();
    }

    Session2CommandGroup(Parcel in) {
        mCommands = new LinkedHashSet<Session2Command>();
        int count = in.readInt();
        for (int i = 0; i < count; i++) {
            mCommands.add(Session2Command.CREATOR.createFromParcel(in));
        }
    }

    public boolean hasCommand(Session2Command command) {
        Objects.requireNonNull(command, "command");
        return mCommands.contains(command);
    }

    public boolean hasCommand(int commandCode) {
        if (commandCode == Session2Command.COMMAND_CODE_CUSTOM) {
            throw new IllegalArgumentException("Use hasCommand(Session2Command) for custom command");
        }
        for (Session2Command cmd : mCommands) {
            if (cmd.getCommandCode() == commandCode) {
                return true;
            }
        }
        return false;
    }

    public Set<Session2Command> getCommands() {
        return Collections.unmodifiableSet(new LinkedHashSet<Session2Command>(mCommands));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        List<Session2Command> list = new ArrayList<Session2Command>(mCommands);
        dest.writeInt(list.size());
        for (int i = 0; i < list.size(); i++) {
            list.get(i).writeToParcel(dest, flags);
        }
    }

    public static final Parcelable.Creator<Session2CommandGroup> CREATOR =
            new Parcelable.Creator<Session2CommandGroup>() {
                @Override
                public Session2CommandGroup createFromParcel(Parcel in) {
                    return new Session2CommandGroup(in);
                }

                @Override
                public Session2CommandGroup[] newArray(int size) {
                    return new Session2CommandGroup[size];
                }
            };

    public static final class Builder {
        private final Set<Session2Command> mCommands = new LinkedHashSet<Session2Command>();

        public Builder() {}

        public Builder(Session2CommandGroup commandGroup) {
            Objects.requireNonNull(commandGroup, "commandGroup");
            mCommands.addAll(commandGroup.getCommands());
        }

        public Builder addCommand(Session2Command command) {
            Objects.requireNonNull(command, "command");
            mCommands.add(command);
            return this;
        }

        public Builder removeCommand(Session2Command command) {
            Objects.requireNonNull(command, "command");
            mCommands.remove(command);
            return this;
        }

        public Session2CommandGroup build() {
            return new Session2CommandGroup(mCommands);
        }
    }
}
