// SPDX-License-Identifier: GPL-3.0-only
package android.content.res.loader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Ordered container of ResourcesProviders overlaid onto a Resources instance. */
public class ResourcesLoader {
    private final Object mLock = new Object();
    private final List<ResourcesProvider> mProviders = new ArrayList<ResourcesProvider>();

    public ResourcesLoader() {}

    public List<ResourcesProvider> getProviders() {
        synchronized (mLock) {
            return Collections.unmodifiableList(new ArrayList<ResourcesProvider>(mProviders));
        }
    }

    public void addProvider(ResourcesProvider resourcesProvider) {
        Objects.requireNonNull(resourcesProvider, "resourcesProvider must not be null");
        synchronized (mLock) {
            if (!mProviders.contains(resourcesProvider)) {
                resourcesProvider.incrementRefCount();
                mProviders.add(resourcesProvider);
            }
        }
    }

    public void removeProvider(ResourcesProvider resourcesProvider) {
        Objects.requireNonNull(resourcesProvider, "resourcesProvider must not be null");
        synchronized (mLock) {
            if (mProviders.remove(resourcesProvider)) {
                resourcesProvider.decrementRefCount();
            }
        }
    }

    public void setProviders(List<ResourcesProvider> resourcesProviders) {
        Objects.requireNonNull(resourcesProviders, "resourcesProviders must not be null");
        synchronized (mLock) {
            for (ResourcesProvider old : mProviders) {
                old.decrementRefCount();
            }
            mProviders.clear();
            for (ResourcesProvider p : resourcesProviders) {
                Objects.requireNonNull(p);
                if (!mProviders.contains(p)) {
                    p.incrementRefCount();
                    mProviders.add(p);
                }
            }
        }
    }

    public void clearProviders() {
        synchronized (mLock) {
            for (ResourcesProvider old : mProviders) {
                old.decrementRefCount();
            }
            mProviders.clear();
        }
    }
}
