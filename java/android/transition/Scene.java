// SPDX-License-Identifier: GPL-3.0-only
package android.transition;

import android.content.Context;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import java.util.WeakHashMap;

/** Encapsulates a view-hierarchy state inside a {@link ViewGroup} scene root. */
public final class Scene {
    private static final WeakHashMap<ViewGroup, Scene> CURRENT_SCENES = new WeakHashMap<ViewGroup, Scene>();
    private static final WeakHashMap<ViewGroup, SparseArray<Scene>> LAYOUT_CACHE =
            new WeakHashMap<ViewGroup, SparseArray<Scene>>();

    private final Context context;
    private final int layoutId;
    private final ViewGroup sceneRoot;
    private final View layout;
    private Runnable enterAction;
    private Runnable exitAction;

    public Scene(ViewGroup sceneRoot) {
        this.sceneRoot = sceneRoot;
        this.context = null;
        this.layoutId = -1;
        this.layout = null;
    }

    private Scene(ViewGroup sceneRoot, int layoutId, Context context) {
        this.context = context;
        this.sceneRoot = sceneRoot;
        this.layoutId = layoutId;
        this.layout = null;
    }

    public Scene(ViewGroup sceneRoot, View layout) {
        this.sceneRoot = sceneRoot;
        this.layout = layout;
        this.context = null;
        this.layoutId = -1;
    }

    @Deprecated
    public Scene(ViewGroup sceneRoot, ViewGroup layout) {
        this(sceneRoot, (View) layout);
    }

    public static Scene getSceneForLayout(ViewGroup sceneRoot, int layoutId, Context context) {
        synchronized (LAYOUT_CACHE) {
            SparseArray<Scene> scenes = LAYOUT_CACHE.get(sceneRoot);
            if (scenes == null) {
                scenes = new SparseArray<Scene>();
                LAYOUT_CACHE.put(sceneRoot, scenes);
            }
            Scene scene = scenes.get(layoutId);
            if (scene != null) {
                return scene;
            }
            scene = new Scene(sceneRoot, layoutId, context);
            scenes.put(layoutId, scene);
            return scene;
        }
    }

    public ViewGroup getSceneRoot() {
        return sceneRoot;
    }

    public void exit() {
        if (getCurrentScene(sceneRoot) == this && exitAction != null) {
            exitAction.run();
        }
    }

    public void enter() {
        if (layoutId > 0 || layout != null) {
            getSceneRoot().removeAllViews();
            if (layoutId > 0) {
                LayoutInflater.from(context).inflate(layoutId, sceneRoot);
            } else {
                sceneRoot.addView(layout);
            }
        }
        if (enterAction != null) {
            enterAction.run();
        }
        setCurrentScene(sceneRoot, this);
    }

    static void setCurrentScene(ViewGroup sceneRoot, Scene scene) {
        synchronized (CURRENT_SCENES) {
            if (scene == null) {
                CURRENT_SCENES.remove(sceneRoot);
            } else {
                CURRENT_SCENES.put(sceneRoot, scene);
            }
        }
    }

    public static Scene getCurrentScene(ViewGroup sceneRoot) {
        synchronized (CURRENT_SCENES) {
            return CURRENT_SCENES.get(sceneRoot);
        }
    }

    public void setEnterAction(Runnable action) {
        this.enterAction = action;
    }

    public void setExitAction(Runnable action) {
        this.exitAction = action;
    }

    boolean isCreatedFromLayoutResource() {
        return layoutId > 0;
    }
}
