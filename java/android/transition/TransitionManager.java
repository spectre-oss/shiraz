// SPDX-License-Identifier: GPL-3.0-only
package android.transition;

import android.util.ArrayMap;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/** Orchestrates {@link Scene} transitions and delayed hierarchy transitions on {@link ViewGroup}s. */
public class TransitionManager {
    private static final Transition DEFAULT_TRANSITION = new AutoTransition();
    private static final ThreadLocal<WeakReference<ArrayMap<ViewGroup, ArrayList<Transition>>>>
            RUNNING_TRANSITIONS =
                    new ThreadLocal<WeakReference<ArrayMap<ViewGroup, ArrayList<Transition>>>>();
    private static final ArrayList<ViewGroup> PENDING_TRANSITIONS = new ArrayList<ViewGroup>();

    private final ArrayMap<Scene, Transition> sceneTransitions = new ArrayMap<Scene, Transition>();
    private final ArrayMap<Scene, ArrayMap<Scene, Transition>> scenePairTransitions =
            new ArrayMap<Scene, ArrayMap<Scene, Transition>>();

    public TransitionManager() {}

    public void setTransition(Scene scene, Transition transition) {
        sceneTransitions.put(scene, transition);
    }

    public void setTransition(Scene fromScene, Scene toScene, Transition transition) {
        ArrayMap<Scene, Transition> sceneTransitionMap = scenePairTransitions.get(toScene);
        if (sceneTransitionMap == null) {
            sceneTransitionMap = new ArrayMap<Scene, Transition>();
            scenePairTransitions.put(toScene, sceneTransitionMap);
        }
        sceneTransitionMap.put(fromScene, transition);
    }

    private Transition getTransition(Scene scene) {
        ViewGroup sceneRoot = scene.getSceneRoot();
        if (sceneRoot != null) {
            Scene currScene = Scene.getCurrentScene(sceneRoot);
            if (currScene != null) {
                ArrayMap<Scene, Transition> sceneTransitionMap = scenePairTransitions.get(scene);
                if (sceneTransitionMap != null) {
                    Transition transition = sceneTransitionMap.get(currScene);
                    if (transition != null) {
                        return transition;
                    }
                }
            }
        }
        Transition transition = sceneTransitions.get(scene);
        return transition != null ? transition : DEFAULT_TRANSITION;
    }

    private static ArrayMap<ViewGroup, ArrayList<Transition>> getRunningTransitions() {
        WeakReference<ArrayMap<ViewGroup, ArrayList<Transition>>> runningTransitions =
                RUNNING_TRANSITIONS.get();
        if (runningTransitions != null) {
            ArrayMap<ViewGroup, ArrayList<Transition>> transitions = runningTransitions.get();
            if (transitions != null) {
                return transitions;
            }
        }
        ArrayMap<ViewGroup, ArrayList<Transition>> transitions =
                new ArrayMap<ViewGroup, ArrayList<Transition>>();
        RUNNING_TRANSITIONS.set(
                new WeakReference<ArrayMap<ViewGroup, ArrayList<Transition>>>(transitions));
        return transitions;
    }

    private static void changeScene(Scene scene, Transition transition) {
        final ViewGroup sceneRoot = scene.getSceneRoot();
        if (PENDING_TRANSITIONS.contains(sceneRoot)) {
            return;
        }
        Scene oldScene = Scene.getCurrentScene(sceneRoot);
        if (transition == null) {
            if (oldScene != null) {
                oldScene.exit();
            }
            scene.enter();
            return;
        }
        PENDING_TRANSITIONS.add(sceneRoot);
        Transition transitionClone = transition.clone();
        if (oldScene != null && oldScene.isCreatedFromLayoutResource()) {
            transitionClone.setCanRemoveViews(true);
        }
        sceneChangeSetup(sceneRoot, transitionClone);
        scene.enter();
        sceneChangeRunTransition(sceneRoot, transitionClone);
    }

    private static void sceneChangeSetup(ViewGroup sceneRoot, Transition transition) {
        ArrayList<Transition> runningTransitions = getRunningTransitions().get(sceneRoot);
        if (runningTransitions != null && !runningTransitions.isEmpty()) {
            for (int i = 0; i < runningTransitions.size(); i++) {
                runningTransitions.get(i).pause(sceneRoot);
            }
        }
        if (transition != null) {
            transition.captureValues(sceneRoot, true);
        }
        Scene previousScene = Scene.getCurrentScene(sceneRoot);
        if (previousScene != null) {
            previousScene.exit();
        }
    }

    private static void sceneChangeRunTransition(
            final ViewGroup sceneRoot, final Transition transition) {
        if (transition != null && sceneRoot != null) {
            MultiListener listener = new MultiListener(transition, sceneRoot);
            sceneRoot.addOnAttachStateChangeListener(listener);
            sceneRoot.getViewTreeObserver().addOnPreDrawListener(listener);
        } else {
            PENDING_TRANSITIONS.remove(sceneRoot);
        }
    }

    public void transitionTo(Scene scene) {
        changeScene(scene, getTransition(scene));
    }

    public static void go(Scene scene) {
        changeScene(scene, DEFAULT_TRANSITION);
    }

    public static void go(Scene scene, Transition transition) {
        changeScene(scene, transition);
    }

    public static void beginDelayedTransition(ViewGroup sceneRoot) {
        beginDelayedTransition(sceneRoot, null);
    }

    public static void beginDelayedTransition(ViewGroup sceneRoot, Transition transition) {
        if (!PENDING_TRANSITIONS.contains(sceneRoot) && sceneRoot != null) {
            PENDING_TRANSITIONS.add(sceneRoot);
            Transition actual = transition == null ? DEFAULT_TRANSITION : transition;
            Transition transitionClone = actual.clone();
            sceneChangeSetup(sceneRoot, transitionClone);
            Scene.setCurrentScene(sceneRoot, null);
            sceneChangeRunTransition(sceneRoot, transitionClone);
        }
    }

    public static void endTransitions(ViewGroup sceneRoot) {
        PENDING_TRANSITIONS.remove(sceneRoot);
        ArrayList<Transition> runningTransitions = getRunningTransitions().get(sceneRoot);
        if (runningTransitions != null && !runningTransitions.isEmpty()) {
            ArrayList<Transition> copy = new ArrayList<Transition>(runningTransitions);
            for (int i = copy.size() - 1; i >= 0; i--) {
                copy.get(i).cancel();
            }
        }
    }

    private static final class MultiListener
            implements ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
        final Transition transition;
        final ViewGroup sceneRoot;

        MultiListener(Transition transition, ViewGroup sceneRoot) {
            this.transition = transition;
            this.sceneRoot = sceneRoot;
        }

        private void removeListeners() {
            sceneRoot.getViewTreeObserver().removeOnPreDrawListener(this);
            sceneRoot.removeOnAttachStateChangeListener(this);
        }

        @Override
        public void onViewAttachedToWindow(View v) {}

        @Override
        public void onViewDetachedFromWindow(View v) {
            removeListeners();
            PENDING_TRANSITIONS.remove(sceneRoot);
            ArrayList<Transition> runningTransitions = getRunningTransitions().get(sceneRoot);
            if (runningTransitions != null && !runningTransitions.isEmpty()) {
                for (int i = 0; i < runningTransitions.size(); i++) {
                    runningTransitions.get(i).resume(sceneRoot);
                }
            }
            transition.clearValues(true);
        }

        @Override
        public boolean onPreDraw() {
            removeListeners();
            if (!PENDING_TRANSITIONS.remove(sceneRoot)) {
                return true;
            }
            final ArrayMap<ViewGroup, ArrayList<Transition>> runningTransitions =
                    getRunningTransitions();
            ArrayList<Transition> currentTransitions = runningTransitions.get(sceneRoot);
            ArrayList<Transition> previousRunningTransitions = null;
            if (currentTransitions == null) {
                currentTransitions = new ArrayList<Transition>();
                runningTransitions.put(sceneRoot, currentTransitions);
            } else if (!currentTransitions.isEmpty()) {
                previousRunningTransitions = new ArrayList<Transition>(currentTransitions);
            }
            currentTransitions.add(transition);
            transition.addListener(
                    new TransitionListenerAdapter() {
                        @Override
                        public void onTransitionEnd(Transition completed) {
                            ArrayList<Transition> current = runningTransitions.get(sceneRoot);
                            if (current != null) {
                                current.remove(completed);
                            }
                            completed.removeListener(this);
                        }
                    });
            transition.captureValues(sceneRoot, false);
            if (previousRunningTransitions != null) {
                for (int i = 0; i < previousRunningTransitions.size(); i++) {
                    previousRunningTransitions.get(i).resume(sceneRoot);
                }
            }
            transition.playTransition(sceneRoot);
            return true;
        }
    }
}
