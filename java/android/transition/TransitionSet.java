// SPDX-License-Identifier: GPL-3.0-only
package android.transition;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;

/** Composite {@link Transition} running child transitions together or sequentially. */
public class TransitionSet extends Transition {
    public static final int ORDERING_TOGETHER = 0;
    public static final int ORDERING_SEQUENTIAL = 1;

    private ArrayList<Transition> transitions = new ArrayList<Transition>();
    private boolean playTogether = true;
    int currentListeners;
    boolean started;

    public TransitionSet() {}

    public TransitionSet(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TransitionSet setOrdering(int ordering) {
        switch (ordering) {
            case ORDERING_SEQUENTIAL:
                playTogether = false;
                break;
            case ORDERING_TOGETHER:
                playTogether = true;
                break;
            default:
                throw new IllegalArgumentException("Invalid parameter for TransitionSet ordering: " + ordering);
        }
        return this;
    }

    public int getOrdering() {
        return playTogether ? ORDERING_TOGETHER : ORDERING_SEQUENTIAL;
    }

    public TransitionSet addTransition(Transition transition) {
        if (transition != null) {
            addTransitionInternal(transition);
            if (duration >= 0L) {
                transition.setDuration(duration);
            }
            if (getInterpolator() != null) {
                transition.setInterpolator(getInterpolator());
            }
            if (propagation != null) {
                transition.setPropagation(propagation);
            }
            if (getEpicenterCallback() != null) {
                transition.setEpicenterCallback(getEpicenterCallback());
            }
        }
        return this;
    }

    private void addTransitionInternal(Transition transition) {
        transitions.add(transition);
        transition.parent = this;
    }

    public int getTransitionCount() {
        return transitions.size();
    }

    public Transition getTransitionAt(int index) {
        if (index < 0 || index >= transitions.size()) {
            return null;
        }
        return transitions.get(index);
    }

    @Override
    public TransitionSet setDuration(long duration) {
        super.setDuration(duration);
        if (this.duration >= 0L && transitions != null) {
            for (int i = 0; i < transitions.size(); i++) {
                transitions.get(i).setDuration(duration);
            }
        }
        return this;
    }

    @Override
    public TransitionSet setStartDelay(long startDelay) {
        return (TransitionSet) super.setStartDelay(startDelay);
    }

    @Override
    public TransitionSet setInterpolator(TimeInterpolator interpolator) {
        super.setInterpolator(interpolator);
        if (transitions != null) {
            for (int i = 0; i < transitions.size(); i++) {
                transitions.get(i).setInterpolator(interpolator);
            }
        }
        return this;
    }

    @Override
    public TransitionSet addTarget(View target) {
        for (int i = 0; i < transitions.size(); i++) {
            transitions.get(i).addTarget(target);
        }
        return (TransitionSet) super.addTarget(target);
    }

    @Override
    public TransitionSet addTarget(int targetId) {
        for (int i = 0; i < transitions.size(); i++) {
            transitions.get(i).addTarget(targetId);
        }
        return (TransitionSet) super.addTarget(targetId);
    }

    @Override
    public TransitionSet addTarget(String targetName) {
        for (int i = 0; i < transitions.size(); i++) {
            transitions.get(i).addTarget(targetName);
        }
        return (TransitionSet) super.addTarget(targetName);
    }

    @Override
    public TransitionSet addTarget(Class targetType) {
        for (int i = 0; i < transitions.size(); i++) {
            transitions.get(i).addTarget(targetType);
        }
        return (TransitionSet) super.addTarget(targetType);
    }

    @Override
    public TransitionSet addListener(TransitionListener listener) {
        return (TransitionSet) super.addListener(listener);
    }

    @Override
    public TransitionSet removeTarget(int targetId) {
        for (int i = 0; i < transitions.size(); i++) {
            transitions.get(i).removeTarget(targetId);
        }
        return (TransitionSet) super.removeTarget(targetId);
    }

    @Override
    public TransitionSet removeTarget(View target) {
        for (int i = 0; i < transitions.size(); i++) {
            transitions.get(i).removeTarget(target);
        }
        return (TransitionSet) super.removeTarget(target);
    }

    @Override
    public TransitionSet removeTarget(Class target) {
        for (int i = 0; i < transitions.size(); i++) {
            transitions.get(i).removeTarget(target);
        }
        return (TransitionSet) super.removeTarget(target);
    }

    @Override
    public TransitionSet removeTarget(String target) {
        for (int i = 0; i < transitions.size(); i++) {
            transitions.get(i).removeTarget(target);
        }
        return (TransitionSet) super.removeTarget(target);
    }

    @Override
    public Transition excludeTarget(View target, boolean exclude) {
        for (int i = 0; i < transitions.size(); i++) {
            transitions.get(i).excludeTarget(target, exclude);
        }
        return super.excludeTarget(target, exclude);
    }

    @Override
    public Transition excludeTarget(String targetName, boolean exclude) {
        for (int i = 0; i < transitions.size(); i++) {
            transitions.get(i).excludeTarget(targetName, exclude);
        }
        return super.excludeTarget(targetName, exclude);
    }

    @Override
    public Transition excludeTarget(int targetId, boolean exclude) {
        for (int i = 0; i < transitions.size(); i++) {
            transitions.get(i).excludeTarget(targetId, exclude);
        }
        return super.excludeTarget(targetId, exclude);
    }

    @Override
    public Transition excludeTarget(Class type, boolean exclude) {
        for (int i = 0; i < transitions.size(); i++) {
            transitions.get(i).excludeTarget(type, exclude);
        }
        return super.excludeTarget(type, exclude);
    }

    @Override
    public TransitionSet removeListener(TransitionListener listener) {
        return (TransitionSet) super.removeListener(listener);
    }

    @Override
    public void setPathMotion(PathMotion pathMotion) {
        super.setPathMotion(pathMotion);
        for (int i = 0; i < transitions.size(); i++) {
            transitions.get(i).setPathMotion(pathMotion);
        }
    }

    public TransitionSet removeTransition(Transition transition) {
        transitions.remove(transition);
        if (transition != null) {
            transition.parent = null;
        }
        return this;
    }

    @Override
    public void captureStartValues(TransitionValues transitionValues) {
        if (isValidTarget(transitionValues.view)) {
            for (int i = 0; i < transitions.size(); i++) {
                Transition child = transitions.get(i);
                if (child.isValidTarget(transitionValues.view)) {
                    child.captureStartValues(transitionValues);
                    transitionValues.targetedTransitions.add(child);
                }
            }
        }
    }

    @Override
    public void captureEndValues(TransitionValues transitionValues) {
        if (isValidTarget(transitionValues.view)) {
            for (int i = 0; i < transitions.size(); i++) {
                Transition child = transitions.get(i);
                if (child.isValidTarget(transitionValues.view)) {
                    child.captureEndValues(transitionValues);
                    transitionValues.targetedTransitions.add(child);
                }
            }
        }
    }

    @Override
    void capturePropagationValues(TransitionValues transitionValues) {
        super.capturePropagationValues(transitionValues);
        for (int i = 0; i < transitions.size(); i++) {
            transitions.get(i).capturePropagationValues(transitionValues);
        }
    }

    @Override
    public void setPropagation(TransitionPropagation propagation) {
        super.setPropagation(propagation);
        for (int i = 0; i < transitions.size(); i++) {
            transitions.get(i).setPropagation(propagation);
        }
    }

    @Override
    public void setEpicenterCallback(EpicenterCallback epicenterCallback) {
        super.setEpicenterCallback(epicenterCallback);
        for (int i = 0; i < transitions.size(); i++) {
            transitions.get(i).setEpicenterCallback(epicenterCallback);
        }
    }

    @Override
    void createAnimators(
            ViewGroup sceneRoot,
            ArrayList<TransitionValues> startList,
            ArrayList<TransitionValues> endList) {
        long startDelay = getStartDelay();
        int numTransitions = transitions.size();
        for (int i = 0; i < numTransitions; i++) {
            Transition childTransition = transitions.get(i);
            if (startDelay > 0L && (playTogether || i == 0)) {
                long childStartDelay = childTransition.getStartDelay();
                if (childStartDelay > 0L) {
                    childTransition.setStartDelay(startDelay + childStartDelay);
                } else {
                    childTransition.setStartDelay(startDelay);
                }
            }
            childTransition.createAnimators(sceneRoot, startList, endList);
        }
    }

    @Override
    protected void runAnimators() {
        if (transitions.isEmpty()) {
            start();
            end();
            return;
        }
        currentListeners = transitions.size();
        started = false;
        TransitionListener setListener =
                new TransitionListenerAdapter() {
                    @Override
                    public void onTransitionStart(Transition transition) {
                        if (!started) {
                            start();
                            started = true;
                        }
                    }

                    @Override
                    public void onTransitionEnd(Transition transition) {
                        currentListeners--;
                        if (currentListeners == 0) {
                            started = false;
                            end();
                        }
                        transition.removeListener(this);
                    }
                };
        for (int i = 0; i < transitions.size(); i++) {
            transitions.get(i).addListener(setListener);
        }
        if (!playTogether) {
            for (int i = 1; i < transitions.size(); i++) {
                Transition prev = transitions.get(i - 1);
                final Transition next = transitions.get(i);
                prev.addListener(
                        new TransitionListenerAdapter() {
                            @Override
                            public void onTransitionEnd(Transition transition) {
                                next.runAnimators();
                                transition.removeListener(this);
                            }
                        });
            }
            Transition first = transitions.get(0);
            if (first != null) {
                first.runAnimators();
            }
        } else {
            for (int i = 0; i < transitions.size(); i++) {
                transitions.get(i).runAnimators();
            }
        }
    }

    @Override
    public void pause(View sceneRoot) {
        super.pause(sceneRoot);
        for (int i = 0; i < transitions.size(); i++) {
            transitions.get(i).pause(sceneRoot);
        }
    }

    @Override
    public void resume(View sceneRoot) {
        super.resume(sceneRoot);
        for (int i = 0; i < transitions.size(); i++) {
            transitions.get(i).resume(sceneRoot);
        }
    }

    @Override
    protected void cancel() {
        super.cancel();
        for (int i = 0; i < transitions.size(); i++) {
            transitions.get(i).cancel();
        }
    }

    @Override
    public TransitionSet clone() {
        TransitionSet clone = (TransitionSet) super.clone();
        clone.transitions = new ArrayList<Transition>();
        for (int i = 0; i < transitions.size(); i++) {
            clone.addTransitionInternal(transitions.get(i).clone());
        }
        return clone;
    }
}
