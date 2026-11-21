// SPDX-License-Identifier: GPL-3.0-only
package android.transition;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import java.util.ArrayList;
import java.util.List;

/** Base scene-change transition capturing view values and running {@link Animator}s. */
public abstract class Transition implements Cloneable {
    public static final int MATCH_INSTANCE = 1;
    public static final int MATCH_NAME = 2;
    public static final int MATCH_ID = 3;
    public static final int MATCH_ITEM_ID = 4;

    private static final int[] DEFAULT_MATCH_ORDER = {
        MATCH_NAME,
        MATCH_INSTANCE,
        MATCH_ID,
        MATCH_ITEM_ID,
    };

    private static final PathMotion STRAIGHT_PATH_MOTION =
            new PathMotion() {
                @Override
                public Path getPath(float startX, float startY, float endX, float endY) {
                    Path path = new Path();
                    path.moveTo(startX, startY);
                    path.lineTo(endX, endY);
                    return path;
                }
            };

    private final String name = getClass().getName();
    long startDelay = -1L;
    long duration = -1L;
    private TimeInterpolator interpolator;
    ArrayList<Integer> targetIds = new ArrayList<Integer>();
    ArrayList<View> targets = new ArrayList<View>();
    private ArrayList<String> targetNames;
    private ArrayList<Class> targetTypes;
    private ArrayList<Integer> targetIdExcludes;
    private ArrayList<View> targetExcludes;
    private ArrayList<Class> targetTypeExcludes;
    private ArrayList<String> targetNameExcludes;
    private ArrayList<Integer> targetIdChildExcludes;
    private ArrayList<View> targetChildExcludes;
    private ArrayList<Class> targetTypeChildExcludes;
    TransitionSet parent;
    private int[] matchOrder = DEFAULT_MATCH_ORDER;
    boolean canRemoveViews;
    private ArrayList<Animator> currentAnimators = new ArrayList<Animator>();
    private int numInstances;
    private boolean paused;
    private boolean ended;
    private ArrayList<TransitionListener> listeners;
    private ArrayList<Animator> animators = new ArrayList<Animator>();
    TransitionPropagation propagation;
    private EpicenterCallback epicenterCallback;
    private PathMotion pathMotion = STRAIGHT_PATH_MOTION;

    private ArrayMap<View, TransitionValues> startValuesMap = new ArrayMap<View, TransitionValues>();
    private ArrayMap<View, TransitionValues> endValuesMap = new ArrayMap<View, TransitionValues>();
    private ArrayList<TransitionValues> startValuesList;
    private ArrayList<TransitionValues> endValuesList;

    public interface TransitionListener {
        void onTransitionStart(Transition transition);

        void onTransitionEnd(Transition transition);

        void onTransitionCancel(Transition transition);

        void onTransitionPause(Transition transition);

        void onTransitionResume(Transition transition);
    }

    public abstract static class EpicenterCallback {
        public EpicenterCallback() {}

        public abstract Rect onGetEpicenter(Transition transition);
    }

    public Transition() {}

    public Transition(Context context, AttributeSet attrs) {}

    public Transition setDuration(long duration) {
        this.duration = duration;
        return this;
    }

    public long getDuration() {
        return duration;
    }

    public Transition setStartDelay(long startDelay) {
        this.startDelay = startDelay;
        return this;
    }

    public long getStartDelay() {
        return startDelay;
    }

    public Transition setInterpolator(TimeInterpolator interpolator) {
        this.interpolator = interpolator;
        return this;
    }

    public TimeInterpolator getInterpolator() {
        return interpolator;
    }

    public String[] getTransitionProperties() {
        return null;
    }

    public Animator createAnimator(
            ViewGroup sceneRoot, TransitionValues startValues, TransitionValues endValues) {
        return null;
    }

    public void setMatchOrder(int... matches) {
        if (matches == null || matches.length == 0) {
            matchOrder = DEFAULT_MATCH_ORDER;
            return;
        }
        for (int i = 0; i < matches.length; i++) {
            int match = matches[i];
            if (match < MATCH_INSTANCE || match > MATCH_ITEM_ID) {
                throw new IllegalArgumentException("matches contains invalid value");
            }
            for (int j = 0; j < i; j++) {
                if (matches[j] == match) {
                    throw new IllegalArgumentException("matches contains a duplicate value");
                }
            }
        }
        matchOrder = matches.clone();
    }

    public abstract void captureStartValues(TransitionValues transitionValues);

    public abstract void captureEndValues(TransitionValues transitionValues);

    public Transition addTarget(int targetId) {
        if (targetId != 0) {
            targetIds.add(Integer.valueOf(targetId));
        }
        return this;
    }

    public Transition addTarget(String targetName) {
        if (targetName != null) {
            if (targetNames == null) {
                targetNames = new ArrayList<String>();
            }
            targetNames.add(targetName);
        }
        return this;
    }

    public Transition addTarget(Class targetType) {
        if (targetType != null) {
            if (targetTypes == null) {
                targetTypes = new ArrayList<Class>();
            }
            targetTypes.add(targetType);
        }
        return this;
    }

    public Transition addTarget(View target) {
        if (target != null) {
            targets.add(target);
        }
        return this;
    }

    public Transition removeTarget(int targetId) {
        if (targetId != 0) {
            targetIds.remove(Integer.valueOf(targetId));
        }
        return this;
    }

    public Transition removeTarget(String targetName) {
        if (targetName != null && targetNames != null) {
            targetNames.remove(targetName);
        }
        return this;
    }

    public Transition removeTarget(View target) {
        if (target != null) {
            targets.remove(target);
        }
        return this;
    }

    public Transition removeTarget(Class target) {
        if (target != null && targetTypes != null) {
            targetTypes.remove(target);
        }
        return this;
    }

    private static <T> ArrayList<T> excludeObject(ArrayList<T> list, T target, boolean exclude) {
        ArrayList<T> out = list;
        if (target != null) {
            if (exclude) {
                if (out == null) {
                    out = new ArrayList<T>();
                }
                if (!out.contains(target)) {
                    out.add(target);
                }
            } else if (out != null) {
                out.remove(target);
                if (out.isEmpty()) {
                    out = null;
                }
            }
        }
        return out;
    }

    public Transition excludeTarget(int targetId, boolean exclude) {
        if (targetId > 0) {
            targetIdExcludes = excludeObject(targetIdExcludes, Integer.valueOf(targetId), exclude);
        }
        return this;
    }

    public Transition excludeTarget(String targetName, boolean exclude) {
        targetNameExcludes = excludeObject(targetNameExcludes, targetName, exclude);
        return this;
    }

    public Transition excludeChildren(int targetId, boolean exclude) {
        if (targetId > 0) {
            targetIdChildExcludes = excludeObject(targetIdChildExcludes, Integer.valueOf(targetId), exclude);
        }
        return this;
    }

    public Transition excludeTarget(View target, boolean exclude) {
        targetExcludes = excludeObject(targetExcludes, target, exclude);
        return this;
    }

    public Transition excludeChildren(View target, boolean exclude) {
        targetChildExcludes = excludeObject(targetChildExcludes, target, exclude);
        return this;
    }

    public Transition excludeTarget(Class type, boolean exclude) {
        targetTypeExcludes = excludeObject(targetTypeExcludes, type, exclude);
        return this;
    }

    public Transition excludeChildren(Class type, boolean exclude) {
        targetTypeChildExcludes = excludeObject(targetTypeChildExcludes, type, exclude);
        return this;
    }

    public List<Integer> getTargetIds() {
        return targetIds;
    }

    public List<View> getTargets() {
        return targets;
    }

    public List<String> getTargetNames() {
        return targetNames;
    }

    public List<Class> getTargetTypes() {
        return targetTypes;
    }

    boolean isValidTarget(View target) {
        if (target == null) {
            return false;
        }
        int targetId = target.getId();
        if (targetIdExcludes != null && targetIdExcludes.contains(Integer.valueOf(targetId))) {
            return false;
        }
        if (targetExcludes != null && targetExcludes.contains(target)) {
            return false;
        }
        if (targetTypeExcludes != null) {
            for (int i = 0; i < targetTypeExcludes.size(); i++) {
                if (targetTypeExcludes.get(i).isInstance(target)) {
                    return false;
                }
            }
        }
        if (targetNameExcludes != null
                && target.getTransitionName() != null
                && targetNameExcludes.contains(target.getTransitionName())) {
            return false;
        }
        if (targetIds.isEmpty()
                && targets.isEmpty()
                && (targetTypes == null || targetTypes.isEmpty())
                && (targetNames == null || targetNames.isEmpty())) {
            return true;
        }
        if (targetIds.contains(Integer.valueOf(targetId)) || targets.contains(target)) {
            return true;
        }
        if (targetNames != null && targetNames.contains(target.getTransitionName())) {
            return true;
        }
        if (targetTypes != null) {
            for (int i = 0; i < targetTypes.size(); i++) {
                if (targetTypes.get(i).isInstance(target)) {
                    return true;
                }
            }
        }
        return false;
    }

    void captureValues(ViewGroup sceneRoot, boolean start) {
        clearValues(start);
        boolean hasExplicitTargets =
                (!targetIds.isEmpty() || !targets.isEmpty())
                        && (targetNames == null || targetNames.isEmpty())
                        && (targetTypes == null || targetTypes.isEmpty());
        if (hasExplicitTargets) {
            for (int i = 0; i < targetIds.size(); i++) {
                View view = sceneRoot.findViewById(targetIds.get(i).intValue());
                if (view != null) {
                    recordViewValues(view, start);
                }
            }
            for (int i = 0; i < targets.size(); i++) {
                View view = targets.get(i);
                if (view != null) {
                    recordViewValues(view, start);
                }
            }
        } else {
            captureHierarchy(sceneRoot, start);
        }
    }

    private void recordViewValues(View view, boolean start) {
        TransitionValues values = new TransitionValues(view);
        if (start) {
            captureStartValues(values);
        } else {
            captureEndValues(values);
        }
        values.targetedTransitions.add(this);
        capturePropagationValues(values);
        if (start) {
            startValuesMap.put(view, values);
        } else {
            endValuesMap.put(view, values);
        }
    }

    private void captureHierarchy(View view, boolean start) {
        if (view == null) {
            return;
        }
        int id = view.getId();
        if (targetIdExcludes != null && targetIdExcludes.contains(Integer.valueOf(id))) {
            return;
        }
        if (targetExcludes != null && targetExcludes.contains(view)) {
            return;
        }
        if (targetTypeExcludes != null) {
            for (int i = 0; i < targetTypeExcludes.size(); i++) {
                if (targetTypeExcludes.get(i).isInstance(view)) {
                    return;
                }
            }
        }
        if (view.getParent() instanceof ViewGroup && isValidTarget(view)) {
            recordViewValues(view, start);
        }
        if (view instanceof ViewGroup) {
            if (targetIdChildExcludes != null && targetIdChildExcludes.contains(Integer.valueOf(id))) {
                return;
            }
            if (targetChildExcludes != null && targetChildExcludes.contains(view)) {
                return;
            }
            if (targetTypeChildExcludes != null) {
                for (int i = 0; i < targetTypeChildExcludes.size(); i++) {
                    if (targetTypeChildExcludes.get(i).isInstance(view)) {
                        return;
                    }
                }
            }
            if (view instanceof ListView) {
                return;
            }
            ViewGroup parentGroup = (ViewGroup) view;
            for (int i = 0; i < parentGroup.getChildCount(); i++) {
                captureHierarchy(parentGroup.getChildAt(i), start);
            }
        }
    }

    void capturePropagationValues(TransitionValues transitionValues) {
        if (propagation != null && !transitionValues.values.isEmpty()) {
            String[] propertyNames = propagation.getPropagationProperties();
            if (propertyNames == null) {
                return;
            }
            boolean containsAll = true;
            for (int i = 0; i < propertyNames.length; i++) {
                if (!transitionValues.values.containsKey(propertyNames[i])) {
                    containsAll = false;
                    break;
                }
            }
            if (!containsAll) {
                propagation.captureValues(transitionValues);
            }
        }
    }

    void clearValues(boolean start) {
        if (start) {
            startValuesMap.clear();
            startValuesList = null;
        } else {
            endValuesMap.clear();
            endValuesList = null;
        }
    }

    public TransitionValues getTransitionValues(View view, boolean start) {
        if (parent != null) {
            return parent.getTransitionValues(view, start);
        }
        return start ? startValuesMap.get(view) : endValuesMap.get(view);
    }

    TransitionValues getMatchedTransitionValues(View view, boolean viewInStart) {
        if (parent != null) {
            return parent.getMatchedTransitionValues(view, viewInStart);
        }
        ArrayList<TransitionValues> lookIn = viewInStart ? startValuesList : endValuesList;
        if (lookIn == null) {
            return null;
        }
        int index = -1;
        for (int i = 0; i < lookIn.size(); i++) {
            TransitionValues values = lookIn.get(i);
            if (values != null && values.view == view) {
                index = i;
                break;
            }
        }
        if (index >= 0) {
            ArrayList<TransitionValues> matchIn = viewInStart ? endValuesList : startValuesList;
            return matchIn.get(index);
        }
        return null;
    }

    public boolean isTransitionRequired(TransitionValues startValues, TransitionValues endValues) {
        if (startValues != null && endValues != null) {
            String[] properties = getTransitionProperties();
            if (properties != null) {
                for (int i = 0; i < properties.length; i++) {
                    if (isValueChanged(startValues, endValues, properties[i])) {
                        return true;
                    }
                }
                return false;
            }
            for (String key : startValues.values.keySet()) {
                if (isValueChanged(startValues, endValues, key)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isValueChanged(
            TransitionValues oldValues, TransitionValues newValues, String key) {
        if (oldValues.values.containsKey(key) != newValues.values.containsKey(key)) {
            return false;
        }
        Object oldValue = oldValues.values.get(key);
        Object newValue = newValues.values.get(key);
        if (oldValue == null && newValue == null) {
            return false;
        }
        if (oldValue == null || newValue == null) {
            return true;
        }
        return !oldValue.equals(newValue);
    }

    void playTransition(ViewGroup sceneRoot) {
        ArrayMap<View, TransitionValues> unmatchedStart = new ArrayMap<View, TransitionValues>(startValuesMap);
        ArrayMap<View, TransitionValues> unmatchedEnd = new ArrayMap<View, TransitionValues>(endValuesMap);
        startValuesList = new ArrayList<TransitionValues>();
        endValuesList = new ArrayList<TransitionValues>();

        for (int m = 0; m < matchOrder.length; m++) {
            int mode = matchOrder[m];
            if (mode == MATCH_INSTANCE) {
                for (int i = unmatchedStart.size() - 1; i >= 0; i--) {
                    View view = unmatchedStart.keyAt(i);
                    TransitionValues endVal = unmatchedEnd.remove(view);
                    if (endVal != null) {
                        TransitionValues startVal = unmatchedStart.removeAt(i);
                        startValuesList.add(startVal);
                        endValuesList.add(endVal);
                    }
                }
            } else if (mode == MATCH_NAME) {
                matchByNames(unmatchedStart, unmatchedEnd);
            } else if (mode == MATCH_ID) {
                matchByIds(unmatchedStart, unmatchedEnd);
            }
        }
        for (int i = 0; i < unmatchedStart.size(); i++) {
            startValuesList.add(unmatchedStart.valueAt(i));
            endValuesList.add(null);
        }
        for (int i = 0; i < unmatchedEnd.size(); i++) {
            startValuesList.add(null);
            endValuesList.add(unmatchedEnd.valueAt(i));
        }

        createAnimators(sceneRoot, startValuesList, endValuesList);
        runAnimators();
    }

    private void matchByNames(
            ArrayMap<View, TransitionValues> unmatchedStart,
            ArrayMap<View, TransitionValues> unmatchedEnd) {
        for (int i = unmatchedStart.size() - 1; i >= 0; i--) {
            View startView = unmatchedStart.keyAt(i);
            String startName = startView != null ? startView.getTransitionName() : null;
            if (startName == null) {
                continue;
            }
            for (int j = 0; j < unmatchedEnd.size(); j++) {
                View endView = unmatchedEnd.keyAt(j);
                if (endView != null && startName.equals(endView.getTransitionName())) {
                    startValuesList.add(unmatchedStart.removeAt(i));
                    endValuesList.add(unmatchedEnd.removeAt(j));
                    break;
                }
            }
        }
    }

    private void matchByIds(
            ArrayMap<View, TransitionValues> unmatchedStart,
            ArrayMap<View, TransitionValues> unmatchedEnd) {
        SparseArray<View> endById = new SparseArray<View>();
        for (int j = 0; j < unmatchedEnd.size(); j++) {
            View v = unmatchedEnd.keyAt(j);
            if (v != null && v.getId() != View.NO_ID) {
                endById.put(v.getId(), v);
            }
        }
        for (int i = unmatchedStart.size() - 1; i >= 0; i--) {
            View startView = unmatchedStart.keyAt(i);
            if (startView != null && startView.getId() != View.NO_ID) {
                View endView = endById.get(startView.getId());
                if (endView != null) {
                    TransitionValues endVal = unmatchedEnd.remove(endView);
                    if (endVal != null) {
                        startValuesList.add(unmatchedStart.removeAt(i));
                        endValuesList.add(endVal);
                    }
                }
            }
        }
    }

    void createAnimators(
            ViewGroup sceneRoot,
            ArrayList<TransitionValues> startList,
            ArrayList<TransitionValues> endList) {
        long minStartDelay = Long.MAX_VALUE;
        SparseArray<Long> delays = new SparseArray<Long>();
        int count = startList != null ? startList.size() : 0;
        for (int i = 0; i < count; i++) {
            TransitionValues start = startList.get(i);
            TransitionValues end = endList.get(i);
            if (start != null && !start.targetedTransitions.contains(this)) {
                start = null;
            }
            if (end != null && !end.targetedTransitions.contains(this)) {
                end = null;
            }
            if (start == null && end == null) {
                continue;
            }
            boolean shouldAnimate =
                    start == null || end == null || isTransitionRequired(start, end);
            if (shouldAnimate) {
                Animator animator = createAnimator(sceneRoot, start, end);
                if (animator != null) {
                    if (propagation != null) {
                        long delay = propagation.getStartDelay(sceneRoot, this, start, end);
                        delays.put(animators.size(), Long.valueOf(delay));
                        minStartDelay = Math.min(delay, minStartDelay);
                    }
                    animators.add(animator);
                }
            }
        }
        if (delays.size() > 0 && minStartDelay != 0L) {
            for (int i = 0; i < delays.size(); i++) {
                int index = delays.keyAt(i);
                Animator animator = animators.get(index);
                long delay = delays.valueAt(i).longValue() - minStartDelay + animator.getStartDelay();
                animator.setStartDelay(Math.max(0L, delay));
            }
        }
    }

    protected void runAnimators() {
        start();
        for (int i = 0; i < animators.size(); i++) {
            final Animator anim = animators.get(i);
            if (anim != null) {
                if (getDuration() >= 0L) {
                    anim.setDuration(getDuration());
                }
                if (getStartDelay() >= 0L) {
                    anim.setStartDelay(getStartDelay() + anim.getStartDelay());
                }
                if (getInterpolator() != null) {
                    anim.setInterpolator(getInterpolator());
                }
                anim.addListener(
                        new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationStart(Animator animation) {
                                currentAnimators.add(animation);
                            }

                            @Override
                            public void onAnimationEnd(Animator animation) {
                                currentAnimators.remove(animation);
                                animation.removeListener(this);
                                end();
                            }
                        });
                numInstances++;
                anim.start();
            }
        }
        animators.clear();
        if (numInstances == 0) {
            end();
        }
    }

    protected void start() {
        if (numInstances == 0) {
            if (listeners != null && !listeners.isEmpty()) {
                ArrayList<TransitionListener> tmp = new ArrayList<TransitionListener>(listeners);
                for (int i = 0; i < tmp.size(); i++) {
                    tmp.get(i).onTransitionStart(this);
                }
            }
            ended = false;
        }
    }

    protected void end() {
        if (numInstances > 0) {
            numInstances--;
        }
        if (numInstances == 0 && !ended) {
            ended = true;
            if (listeners != null && !listeners.isEmpty()) {
                ArrayList<TransitionListener> tmp = new ArrayList<TransitionListener>(listeners);
                for (int i = 0; i < tmp.size(); i++) {
                    tmp.get(i).onTransitionEnd(this);
                }
            }
        }
    }

    protected void cancel() {
        for (int i = currentAnimators.size() - 1; i >= 0; i--) {
            currentAnimators.get(i).cancel();
        }
        currentAnimators.clear();
        numInstances = 0;
        if (listeners != null && !listeners.isEmpty()) {
            ArrayList<TransitionListener> tmp = new ArrayList<TransitionListener>(listeners);
            for (int i = 0; i < tmp.size(); i++) {
                tmp.get(i).onTransitionCancel(this);
            }
        }
    }

    public void pause(View sceneRoot) {
        if (!ended) {
            for (int i = currentAnimators.size() - 1; i >= 0; i--) {
                currentAnimators.get(i).pause();
            }
            if (listeners != null && !listeners.isEmpty() && !paused) {
                ArrayList<TransitionListener> tmp = new ArrayList<TransitionListener>(listeners);
                for (int i = 0; i < tmp.size(); i++) {
                    tmp.get(i).onTransitionPause(this);
                }
            }
            paused = true;
        }
    }

    public void resume(View sceneRoot) {
        if (paused) {
            if (!ended) {
                for (int i = currentAnimators.size() - 1; i >= 0; i--) {
                    currentAnimators.get(i).resume();
                }
                if (listeners != null && !listeners.isEmpty()) {
                    ArrayList<TransitionListener> tmp = new ArrayList<TransitionListener>(listeners);
                    for (int i = 0; i < tmp.size(); i++) {
                        tmp.get(i).onTransitionResume(this);
                    }
                }
            }
            paused = false;
        }
    }

    public Transition addListener(TransitionListener listener) {
        if (listeners == null) {
            listeners = new ArrayList<TransitionListener>();
        }
        listeners.add(listener);
        return this;
    }

    public Transition removeListener(TransitionListener listener) {
        if (listeners != null) {
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                listeners = null;
            }
        }
        return this;
    }

    public void setEpicenterCallback(EpicenterCallback epicenterCallback) {
        this.epicenterCallback = epicenterCallback;
    }

    public EpicenterCallback getEpicenterCallback() {
        return epicenterCallback;
    }

    public Rect getEpicenter() {
        if (epicenterCallback == null) {
            return null;
        }
        return epicenterCallback.onGetEpicenter(this);
    }

    public void setPathMotion(PathMotion pathMotion) {
        this.pathMotion = pathMotion == null ? STRAIGHT_PATH_MOTION : pathMotion;
    }

    public PathMotion getPathMotion() {
        return pathMotion;
    }

    public void setPropagation(TransitionPropagation transitionPropagation) {
        this.propagation = transitionPropagation;
    }

    public TransitionPropagation getPropagation() {
        return propagation;
    }

    public boolean canRemoveViews() {
        return canRemoveViews;
    }

    void setCanRemoveViews(boolean canRemoveViews) {
        this.canRemoveViews = canRemoveViews;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return toString("");
    }

    String toString(String indent) {
        StringBuilder result = new StringBuilder(indent);
        result.append(getClass().getSimpleName()).append("@").append(Integer.toHexString(hashCode()));
        if (duration != -1L) {
            result.append(": dur(").append(duration).append(") ");
        }
        if (startDelay != -1L) {
            result.append("dly(").append(startDelay).append(") ");
        }
        if (interpolator != null) {
            result.append("interp(").append(interpolator).append(") ");
        }
        return result.toString();
    }

    @Override
    public Transition clone() {
        try {
            Transition clone = (Transition) super.clone();
            clone.animators = new ArrayList<Animator>();
            clone.currentAnimators = new ArrayList<Animator>();
            clone.startValuesMap = new ArrayMap<View, TransitionValues>();
            clone.endValuesMap = new ArrayMap<View, TransitionValues>();
            clone.startValuesList = null;
            clone.endValuesList = null;
            clone.targetIds = new ArrayList<Integer>(targetIds);
            clone.targets = new ArrayList<View>(targets);
            if (targetNames != null) {
                clone.targetNames = new ArrayList<String>(targetNames);
            }
            if (targetTypes != null) {
                clone.targetTypes = new ArrayList<Class>(targetTypes);
            }
            if (listeners != null) {
                clone.listeners = new ArrayList<TransitionListener>(listeners);
            }
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }
}
