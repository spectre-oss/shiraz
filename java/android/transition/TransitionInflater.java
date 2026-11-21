// SPDX-License-Identifier: GPL-3.0-only
package android.transition;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.InflateException;
import android.view.ViewGroup;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/** Inflates {@link Transition} and {@link TransitionManager} hierarchies from XML resources. */
public class TransitionInflater {
    private static final Class<?>[] CONSTRUCTOR_SIGNATURE =
            new Class<?>[] {Context.class, AttributeSet.class};
    private static final HashMap<String, Constructor<?>> CONSTRUCTORS =
            new HashMap<String, Constructor<?>>();

    private final Context context;

    private TransitionInflater(Context context) {
        this.context = context;
    }

    public static TransitionInflater from(Context context) {
        return new TransitionInflater(context);
    }

    public Transition inflateTransition(int resource) {
        XmlResourceParser parser = context.getResources().getXml(resource);
        try {
            return createTransitionFromXml(parser, Xml.asAttributeSet(parser), null);
        } catch (XmlPullParserException e) {
            throw new InflateException(e.getMessage(), e);
        } catch (IOException e) {
            throw new InflateException(
                    parser.getPositionDescription() + ": " + e.getMessage(), e);
        } finally {
            parser.close();
        }
    }

    public TransitionManager inflateTransitionManager(int resource, ViewGroup sceneRoot) {
        XmlResourceParser parser = context.getResources().getXml(resource);
        try {
            return createTransitionManagerFromXml(parser, Xml.asAttributeSet(parser), sceneRoot);
        } catch (XmlPullParserException e) {
            InflateException ex = new InflateException(e.getMessage());
            ex.initCause(e);
            throw ex;
        } catch (IOException e) {
            InflateException ex =
                    new InflateException(parser.getPositionDescription() + ": " + e.getMessage());
            ex.initCause(e);
            throw ex;
        } finally {
            parser.close();
        }
    }

    private Transition createTransitionFromXml(
            XmlPullParser parser, AttributeSet attrs, Transition parent)
            throws XmlPullParserException, IOException {
        Transition transition = null;
        int depth = parser.getDepth();
        TransitionSet transitionSet = parent instanceof TransitionSet ? (TransitionSet) parent : null;
        int type;
        while (((type = parser.next()) != XmlPullParser.END_TAG || parser.getDepth() > depth)
                && type != XmlPullParser.END_DOCUMENT) {
            if (type != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if ("fade".equals(name)) {
                transition = new Fade(context, attrs);
            } else if ("changeBounds".equals(name)) {
                transition = new ChangeBounds(context, attrs);
            } else if ("slide".equals(name)) {
                transition = new Slide(context, attrs);
            } else if ("explode".equals(name)) {
                transition = new Explode(context, attrs);
            } else if ("changeImageTransform".equals(name)) {
                transition = new ChangeImageTransform(context, attrs);
            } else if ("changeTransform".equals(name)) {
                transition = new ChangeTransform(context, attrs);
            } else if ("changeClipBounds".equals(name)) {
                transition = new ChangeClipBounds(context, attrs);
            } else if ("autoTransition".equals(name)) {
                transition = new AutoTransition(context, attrs);
            } else if ("changeScroll".equals(name)) {
                transition = new ChangeScroll(context, attrs);
            } else if ("transitionSet".equals(name)) {
                transition = new TransitionSet(context, attrs);
            } else if ("transition".equals(name)) {
                transition = (Transition) createCustom(attrs, Transition.class, "transition");
            } else if ("targets".equals(name)) {
                getTargetIds(parser, attrs, parent);
            } else if ("arcMotion".equals(name)) {
                if (parent == null) {
                    throw new RuntimeException("Invalid use of arcMotion element");
                }
                parent.setPathMotion(new ArcMotion(context, attrs));
            } else if ("pathMotion".equals(name)) {
                if (parent == null) {
                    throw new RuntimeException("Invalid use of pathMotion element");
                }
                parent.setPathMotion((PathMotion) createCustom(attrs, PathMotion.class, "pathMotion"));
            } else if ("patternPathMotion".equals(name)) {
                if (parent == null) {
                    throw new RuntimeException("Invalid use of patternPathMotion element");
                }
                parent.setPathMotion(new PatternPathMotion(context, attrs));
            } else {
                throw new RuntimeException("Unknown scene name: " + parser.getName());
            }
            if (transition != null) {
                if (!parser.isEmptyElementTag()) {
                    createTransitionFromXml(parser, attrs, transition);
                }
                if (transitionSet != null) {
                    transitionSet.addTransition(transition);
                    transition = null;
                } else if (parent != null) {
                    throw new InflateException("Could not add transition to another transition.");
                }
            }
        }
        return transition;
    }

    private Object createCustom(AttributeSet attrs, Class<?> expectedType, String tag) {
        String className = attrs.getAttributeValue(null, "class");
        if (className == null) {
            throw new InflateException(tag + " tag must have a 'class' attribute");
        }
        try {
            synchronized (CONSTRUCTORS) {
                Constructor<?> constructor = CONSTRUCTORS.get(className);
                if (constructor == null) {
                    Class<?> c =
                            Class.forName(className, false, context.getClassLoader())
                                    .asSubclass(expectedType);
                    constructor = c.getConstructor(CONSTRUCTOR_SIGNATURE);
                    constructor.setAccessible(true);
                    CONSTRUCTORS.put(className, constructor);
                }
                return constructor.newInstance(context, attrs);
            }
        } catch (Exception e) {
            throw new InflateException("Could not instantiate " + expectedType + " class " + className, e);
        }
    }

    private void getTargetIds(XmlPullParser parser, AttributeSet attrs, Transition transition)
            throws XmlPullParserException, IOException {
        int depth = parser.getDepth();
        int type;
        while (((type = parser.next()) != XmlPullParser.END_TAG || parser.getDepth() > depth)
                && type != XmlPullParser.END_DOCUMENT) {
            if (type != XmlPullParser.START_TAG) {
                continue;
            }
            if ("target".equals(parser.getName())) {
                int targetId = attrs.getAttributeResourceValue("http://schemas.android.com/apk/res/android", "targetId", 0);
                if (targetId != 0 && transition != null) {
                    transition.addTarget(targetId);
                }
            } else {
                throw new RuntimeException("Unknown scene name: " + parser.getName());
            }
        }
    }

    private TransitionManager createTransitionManagerFromXml(
            XmlPullParser parser, AttributeSet attrs, ViewGroup sceneRoot)
            throws XmlPullParserException, IOException {
        int depth = parser.getDepth();
        TransitionManager transitionManager = null;
        int type;
        while (((type = parser.next()) != XmlPullParser.END_TAG || parser.getDepth() > depth)
                && type != XmlPullParser.END_DOCUMENT) {
            if (type != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if ("transitionManager".equals(name)) {
                transitionManager = new TransitionManager();
            } else if ("transition".equals(name) && transitionManager != null) {
                loadTransition(attrs, sceneRoot, transitionManager);
            } else {
                throw new RuntimeException("Unknown scene name: " + parser.getName());
            }
        }
        return transitionManager;
    }

    private void loadTransition(
            AttributeSet attrs, ViewGroup sceneRoot, TransitionManager transitionManager)
            throws Resources.NotFoundException {
        String ns = "http://schemas.android.com/apk/res/android";
        int transitionId = attrs.getAttributeResourceValue(ns, "transition", -1);
        int fromId = attrs.getAttributeResourceValue(ns, "fromScene", -1);
        int toId = attrs.getAttributeResourceValue(ns, "toScene", -1);
        Scene fromScene = fromId < 0 ? null : Scene.getSceneForLayout(sceneRoot, fromId, context);
        Scene toScene = toId < 0 ? null : Scene.getSceneForLayout(sceneRoot, toId, context);
        if (transitionId >= 0 && toScene != null) {
            Transition transition = inflateTransition(transitionId);
            if (transition != null) {
                if (fromScene == null) {
                    transitionManager.setTransition(toScene, transition);
                } else {
                    transitionManager.setTransition(fromScene, toScene, transition);
                }
            }
        }
    }
}
