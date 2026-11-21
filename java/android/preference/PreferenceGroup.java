// SPDX-License-Identifier: GPL-3.0-only
package android.preference;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import java.util.*;

public abstract class PreferenceGroup extends Preference {
    private final List<Preference> children = new ArrayList<Preference>();
    private boolean ordering = true;
    private int nextOrder;

    public PreferenceGroup(Context c, AttributeSet a) {
        super(c, a);
    }

    public PreferenceGroup(Context c, AttributeSet a, int style) {
        super(c, a, style);
    }

    public PreferenceGroup(Context c, AttributeSet a, int style, int resource) {
        super(c, a, style, resource);
    }

    public void setOrderingAsAdded(boolean value) {
        ordering = value;
    }

    public boolean isOrderingAsAdded() {
        return ordering;
    }

    protected boolean onPrepareAddPreference(Preference preference) {
        return true;
    }

    public boolean addPreference(Preference value) {
        Objects.requireNonNull(value);
        if (children.contains(value)) {
            return true;
        }
        if (value.parent != null) {
            throw new IllegalStateException("Preference already has a parent");
        }
        int depth = 0;
        for (PreferenceGroup p = this; p != null; p = p.parent) {
            if (p == value) {
                throw new IllegalArgumentException("Preference hierarchy cycle");
            }
            if (++depth > 32) {
                throw new IllegalStateException("Preference hierarchy depth limit");
            }
        }
        if (children.size() >= 1024) {
            throw new IllegalStateException("Preference child limit");
        }
        validateTree(value, getPreferenceManager(), depth);
        if (!onPrepareAddPreference(value)) {
            return false;
        }
        if (ordering && value.getOrder() == DEFAULT_ORDER) {
            value.setOrder(nextOrder++);
        }
        value.parent = this;
        children.add(value);
        if (getPreferenceManager() != null) {
            value.attach(getPreferenceManager());
        }
        notifyHierarchyChanged();
        return true;
    }

    public boolean removePreference(Preference value) {
        if (!children.remove(value)) {
            return false;
        }
        value.parent = null;
        notifyHierarchyChanged();
        return true;
    }

    public void removeAll() {
        for (Preference child : children) {
            child.parent = null;
        }
        children.clear();
        notifyHierarchyChanged();
    }

    public int getPreferenceCount() {
        return children.size();
    }

    public Preference getPreference(int index) {
        Collections.sort(children);
        return children.get(index);
    }

    public Preference findPreference(CharSequence key) {
        Objects.requireNonNull(key);
        if (key.toString().equals(getKey())) {
            return this;
        }
        for (Preference child : children) {
            if (key.toString().equals(child.getKey())) {
                return child;
            }
            if (child instanceof PreferenceGroup) {
                Preference found = ((PreferenceGroup) child).findPreference(key);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void validateTree(Preference value, PreferenceManager manager, int depth) {
        if (depth > 32) {
            throw new IllegalStateException("Preference hierarchy depth limit");
        }
        if (manager != null && value.getPreferenceManager() != null && value.getPreferenceManager() != manager) {
            throw new IllegalStateException("Preference belongs to another manager");
        }
        if (value instanceof PreferenceGroup) {
            for (Preference child : ((PreferenceGroup) value).children) {
                validateTree(child, manager, depth + 1);
            }
        }
    }

    @Override
    void attach(PreferenceManager manager) {
        validateTree(this, manager, 0);
        super.attach(manager);
        for (Preference child : children) {
            child.attach(manager);
        }
    }

    @Override
    void dispatchSaveInstanceState(Bundle container) {
        super.dispatchSaveInstanceState(container);
        for (int i = 0; i < children.size(); i++) {
            children.get(i).dispatchSaveInstanceState(container);
        }
    }

    @Override
    void dispatchRestoreInstanceState(Bundle container) {
        super.dispatchRestoreInstanceState(container);
        for (int i = 0; i < children.size(); i++) {
            children.get(i).dispatchRestoreInstanceState(container);
        }
    }
}
