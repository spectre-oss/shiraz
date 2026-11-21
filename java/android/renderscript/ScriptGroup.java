// SPDX-License-Identifier: GPL-3.0-only
package android.renderscript;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ScriptGroup extends BaseObj {
    static class IO {
        final Script.KernelID mKID;
        Allocation mAllocation;

        IO(Script.KernelID s) {
            mKID = s;
        }
    }

    static class ConnectLine {
        final Type mAllocationType;
        final Script.KernelID mFrom;
        Script.KernelID mToK;
        Script.FieldID mToF;
        Allocation mAllocation;

        ConnectLine(Type t, Script.KernelID from, Script.KernelID to) {
            mFrom = from;
            mToK = to;
            mAllocationType = t;
        }

        ConnectLine(Type t, Script.KernelID from, Script.FieldID to) {
            mFrom = from;
            mToF = to;
            mAllocationType = t;
        }
    }

    static class Node {
        final Script mScript;
        final ArrayList<Script.KernelID> mKernels = new ArrayList<Script.KernelID>();
        final ArrayList<ConnectLine> mInputs = new ArrayList<ConnectLine>();
        final ArrayList<ConnectLine> mOutputs = new ArrayList<ConnectLine>();
        int dagNumber;
        boolean mSeen;
        int mOrder;

        Node(Script s) {
            mScript = s;
        }
    }

    public static final class Closure extends BaseObj {
        private final Script.KernelID mKernelID;
        private final Script.InvokeID mInvokeID;
        private final Type mReturnType;
        private final Object[] mArgs;
        private final Map<Script.FieldID, Object> mBindings;
        private Allocation mReturnValue;
        private Future mReturnFuture;
        private final Map<Script.FieldID, Future> mGlobalFuture = new HashMap<Script.FieldID, Future>();

        Closure(RenderScript rs, Script.KernelID kernelID, Type returnType,
                Object[] args, Map<Script.FieldID, Object> globals) {
            super(0, rs);
            mKernelID = kernelID;
            mInvokeID = null;
            mReturnType = returnType;
            mArgs = args;
            mBindings = globals;
            if (returnType != null) {
                mReturnValue = Allocation.createTyped(rs, returnType);
            }
        }

        Closure(RenderScript rs, Script.InvokeID invokeID,
                Object[] args, Map<Script.FieldID, Object> globals) {
            super(0, rs);
            mKernelID = null;
            mInvokeID = invokeID;
            mReturnType = null;
            mArgs = args;
            mBindings = globals;
        }

        public void destroy() {
            super.destroy();
            if (mReturnValue != null && !mReturnValue.isDestroyed()) {
                mReturnValue.destroy();
            }
        }

        protected void finalize() throws Throwable {
            super.finalize();
        }

        public Future getReturn() {
            if (mReturnFuture == null) {
                mReturnFuture = new Future(this, null, mReturnValue);
            }
            return mReturnFuture;
        }

        public Future getGlobal(Script.FieldID field) {
            Future f = mGlobalFuture.get(field);
            if (f == null) {
                Object obj = mBindings.get(field);
                if (obj instanceof Future) {
                    obj = ((Future) obj).getValue();
                }
                f = new Future(this, field, obj);
                mGlobalFuture.put(field, f);
            }
            return f;
        }

        void evaluate() {
            for (Map.Entry<Script.FieldID, Object> entry : mBindings.entrySet()) {
                Script.FieldID field = entry.getKey();
                Object raw = entry.getValue();
                Object resolved = resolveArg(raw);
                field.mScript.setGlobalField(field.mSlot, resolved);
                Future gf = mGlobalFuture.get(field);
                if (gf != null) {
                    gf.setValue(resolved);
                }
            }

            if (mKernelID != null) {
                ArrayList<Allocation> inList = new ArrayList<Allocation>();
                FieldPacker fp = null;
                for (int i = 0; i < mArgs.length; i++) {
                    Object resolved = resolveArg(mArgs[i]);
                    if (resolved instanceof Allocation) {
                        inList.add((Allocation) resolved);
                    } else if (resolved instanceof FieldPacker) {
                        fp = (FieldPacker) resolved;
                    }
                }
                Allocation[] ains = inList.isEmpty()
                        ? null
                        : inList.toArray(new Allocation[inList.size()]);
                mKernelID.mScript.executeKernel(mKernelID.mSlot, ains, mReturnValue, fp, null);
                if (mReturnFuture != null) {
                    mReturnFuture.setValue(mReturnValue);
                }
            } else if (mInvokeID != null) {
                FieldPacker fp = null;
                if (mArgs != null && mArgs.length > 0) {
                    fp = packInvokeArgs(mArgs);
                }
                mInvokeID.mScript.executeInvoke(mInvokeID.mSlot, fp);
            }
        }

        private static Object resolveArg(Object arg) {
            if (arg instanceof Future) {
                return ((Future) arg).getValue();
            }
            if (arg instanceof Input) {
                return ((Input) arg).get();
            }
            return arg;
        }

        private static FieldPacker packInvokeArgs(Object[] args) {
            int size = 0;
            for (int i = 0; i < args.length; i++) {
                size += 16;
            }
            FieldPacker fp = new FieldPacker(Math.max(16, size));
            for (int i = 0; i < args.length; i++) {
                Object val = resolveArg(args[i]);
                if (val instanceof Integer) {
                    fp.addI32(((Integer) val).intValue());
                } else if (val instanceof Float) {
                    fp.addF32(((Float) val).floatValue());
                } else if (val instanceof Long) {
                    fp.addI64(((Long) val).longValue());
                } else if (val instanceof Double) {
                    fp.addF64(((Double) val).doubleValue());
                } else if (val instanceof Short) {
                    fp.addI16(((Short) val).shortValue());
                } else if (val instanceof Byte) {
                    fp.addI8(((Byte) val).byteValue());
                } else if (val instanceof Boolean) {
                    fp.addBoolean(((Boolean) val).booleanValue());
                } else if (val instanceof BaseObj) {
                    fp.addObj((BaseObj) val);
                }
            }
            return fp;
        }
    }

    public static final class Future {
        private final Closure mClosure;
        private final Script.FieldID mFieldID;
        private Object mValue;

        Future(Closure closure, Script.FieldID fieldID, Object value) {
            mClosure = closure;
            mFieldID = fieldID;
            mValue = value;
        }

        Closure getClosure() {
            return mClosure;
        }

        Script.FieldID getFieldID() {
            return mFieldID;
        }

        Object getValue() {
            return mValue;
        }

        void setValue(Object value) {
            mValue = value;
        }
    }

    public static final class Input {
        private Object mValue;

        Input() {}

        void set(Object value) {
            mValue = value;
        }

        Object get() {
            return mValue;
        }
    }

    IO[] mInputs;
    IO[] mOutputs;
    private ArrayList<Node> mNodes = new ArrayList<Node>();
    private String mName;
    private List<Closure> mClosures;
    private List<Input> mInputs2;
    private Future[] mOutputs2;

    ScriptGroup(long id, RenderScript rs) {
        super(id, rs);
    }

    ScriptGroup(RenderScript rs, String name, List<Closure> closures,
                List<Input> inputs, Future[] outputs) {
        super(0, rs);
        mName = name;
        mClosures = closures;
        mInputs2 = inputs;
        mOutputs2 = outputs;
    }

    public Object[] execute(Object... inputs) {
        mRS.validate();
        checkValid();
        if (mInputs2 == null || mClosures == null) {
            throw new RSInvalidStateException("ScriptGroup was not created with Builder2.");
        }
        int provided = inputs != null ? inputs.length : 0;
        if (provided < mInputs2.size()) {
            throw new RSIllegalArgumentException(
                    "Not enough inputs provided: expected " + mInputs2.size() + ", got " + provided);
        }
        for (int i = 0; i < mInputs2.size(); i++) {
            Object val = inputs[i];
            if (val instanceof Future || val instanceof Input) {
                throw new RSIllegalArgumentException("Cannot pass Future or Input to execute.");
            }
            mInputs2.get(i).set(val);
        }
        for (int i = 0; i < mClosures.size(); i++) {
            mClosures.get(i).evaluate();
        }
        if (mOutputs2 == null) {
            return new Object[0];
        }
        Object[] results = new Object[mOutputs2.length];
        for (int i = 0; i < mOutputs2.length; i++) {
            results[i] = mOutputs2[i] != null ? mOutputs2[i].getValue() : null;
        }
        return results;
    }

    public void setInput(Script.KernelID s, Allocation a) {
        mRS.validate();
        checkValid();
        if (mInputs != null) {
            for (int ct = 0; ct < mInputs.length; ct++) {
                if (mInputs[ct].mKID == s) {
                    mInputs[ct].mAllocation = a;
                    return;
                }
            }
        }
        throw new RSIllegalArgumentException("Script not found");
    }

    public void setOutput(Script.KernelID s, Allocation a) {
        mRS.validate();
        checkValid();
        if (mOutputs != null) {
            for (int ct = 0; ct < mOutputs.length; ct++) {
                if (mOutputs[ct].mKID == s) {
                    mOutputs[ct].mAllocation = a;
                    return;
                }
            }
        }
        throw new RSIllegalArgumentException("Script not found");
    }

    public void execute() {
        mRS.validate();
        checkValid();
        if (mClosures != null) {
            execute(new Object[0]);
            return;
        }
        for (int i = 0; i < mNodes.size(); i++) {
            Node n = mNodes.get(i);
            for (int kIdx = 0; kIdx < n.mKernels.size(); kIdx++) {
                Script.KernelID kid = n.mKernels.get(kIdx);

                for (int inIdx = 0; inIdx < n.mInputs.size(); inIdx++) {
                    ConnectLine cl = n.mInputs.get(inIdx);
                    if (cl.mToF != null && cl.mToF.mScript == n.mScript) {
                        cl.mToF.mScript.setGlobalField(cl.mToF.mSlot, cl.mAllocation);
                    }
                }

                Allocation ain = null;
                for (int inIdx = 0; inIdx < n.mInputs.size(); inIdx++) {
                    ConnectLine cl = n.mInputs.get(inIdx);
                    if (cl.mToK == kid) {
                        ain = cl.mAllocation;
                        break;
                    }
                }
                if (ain == null && mInputs != null) {
                    for (int inIdx = 0; inIdx < mInputs.length; inIdx++) {
                        if (mInputs[inIdx].mKID == kid) {
                            ain = mInputs[inIdx].mAllocation;
                            break;
                        }
                    }
                }

                Allocation aout = null;
                ArrayList<ConnectLine> matchingOutputs = new ArrayList<ConnectLine>();
                for (int outIdx = 0; outIdx < n.mOutputs.size(); outIdx++) {
                    ConnectLine cl = n.mOutputs.get(outIdx);
                    if (cl.mFrom == kid) {
                        if (cl.mAllocation == null) {
                            cl.mAllocation = Allocation.createTyped(mRS, cl.mAllocationType);
                        }
                        if (aout == null) {
                            aout = cl.mAllocation;
                        }
                        matchingOutputs.add(cl);
                    }
                }
                Allocation externalOut = null;
                if (mOutputs != null) {
                    for (int outIdx = 0; outIdx < mOutputs.length; outIdx++) {
                        if (mOutputs[outIdx].mKID == kid) {
                            externalOut = mOutputs[outIdx].mAllocation;
                            break;
                        }
                    }
                }
                if (externalOut != null && aout == null) {
                    aout = externalOut;
                }

                Allocation[] ains = ain != null ? new Allocation[] { ain } : null;
                kid.mScript.executeKernel(kid.mSlot, ains, aout, null, null);

                if (aout != null) {
                    for (int m = 0; m < matchingOutputs.size(); m++) {
                        ConnectLine cl = matchingOutputs.get(m);
                        if (cl.mAllocation != aout) {
                            cl.mAllocation.copyFrom(aout);
                        }
                    }
                    if (externalOut != null && externalOut != aout) {
                        externalOut.copyFrom(aout);
                    }
                }
            }
        }
    }

    public static final class Builder {
        private final RenderScript mRS;
        private final ArrayList<Node> mNodes = new ArrayList<Node>();
        private final ArrayList<ConnectLine> mLines = new ArrayList<ConnectLine>();
        private int mKernelCount;

        public Builder(RenderScript rs) {
            mRS = rs;
        }

        private void validateCycle(Node target, Node original) {
            for (int ct = 0; ct < target.mOutputs.size(); ct++) {
                ConnectLine cl = target.mOutputs.get(ct);
                if (cl.mToK != null) {
                    Node tn = findNode(cl.mToK.mScript);
                    if (tn.equals(original)) {
                        throw new RSInvalidStateException("Loops in group not allowed.");
                    }
                    validateCycle(tn, original);
                }
                if (cl.mToF != null) {
                    Node tn = findNode(cl.mToF.mScript);
                    if (tn.equals(original)) {
                        throw new RSInvalidStateException("Loops in group not allowed.");
                    }
                    validateCycle(tn, original);
                }
            }
        }

        private void mergeDAGs(int valueUsed, int valueKilled) {
            for (int ct = 0; ct < mNodes.size(); ct++) {
                if (mNodes.get(ct).dagNumber == valueKilled) {
                    mNodes.get(ct).dagNumber = valueUsed;
                }
            }
        }

        private void validateDAGRecurse(Node n, int dagNumber) {
            if (n.dagNumber != 0 && n.dagNumber != dagNumber) {
                mergeDAGs(n.dagNumber, dagNumber);
                return;
            }
            n.dagNumber = dagNumber;
            for (int ct = 0; ct < n.mOutputs.size(); ct++) {
                ConnectLine cl = n.mOutputs.get(ct);
                if (cl.mToK != null) {
                    Node tn = findNode(cl.mToK.mScript);
                    validateDAGRecurse(tn, dagNumber);
                }
                if (cl.mToF != null) {
                    Node tn = findNode(cl.mToF.mScript);
                    validateDAGRecurse(tn, dagNumber);
                }
            }
        }

        private void validateDAG() {
            for (int ct = 0; ct < mNodes.size(); ct++) {
                Node n = mNodes.get(ct);
                if (n.mInputs.size() == 0) {
                    if (n.mOutputs.size() == 0 && mNodes.size() > 1) {
                        throw new RSInvalidStateException("Groups cannot contain unconnected scripts");
                    }
                    validateDAGRecurse(n, ct + 1);
                }
            }
            int dagNumber = mNodes.get(0).dagNumber;
            for (int ct = 0; ct < mNodes.size(); ct++) {
                if (mNodes.get(ct).dagNumber != dagNumber) {
                    throw new RSInvalidStateException("Multiple DAGs in group not allowed.");
                }
            }
        }

        private Node findNode(Script s) {
            for (int ct = 0; ct < mNodes.size(); ct++) {
                if (s == mNodes.get(ct).mScript) {
                    return mNodes.get(ct);
                }
            }
            return null;
        }

        private Node findNode(Script.KernelID k) {
            for (int ct = 0; ct < mNodes.size(); ct++) {
                Node n = mNodes.get(ct);
                for (int ct2 = 0; ct2 < n.mKernels.size(); ct2++) {
                    if (k == n.mKernels.get(ct2)) {
                        return n;
                    }
                }
            }
            return null;
        }

        public Builder addKernel(Script.KernelID k) {
            if (mLines.size() != 0) {
                throw new RSInvalidStateException("addKernel cannot be called after addConnection");
            }
            if (findNode(k) != null) {
                return this;
            }
            mKernelCount++;
            Node n = findNode(k.mScript);
            if (n == null) {
                n = new Node(k.mScript);
                mNodes.add(n);
            }
            n.mKernels.add(k);
            return this;
        }

        public Builder addConnection(Type t, Script.KernelID from, Script.FieldID to) {
            Node nf = findNode(from);
            if (nf == null) {
                throw new RSInvalidStateException("From script not found.");
            }
            Node nt = findNode(to.mScript);
            if (nt == null) {
                throw new RSInvalidStateException("To script not found.");
            }
            ConnectLine cl = new ConnectLine(t, from, to);
            mLines.add(cl);
            nf.mOutputs.add(cl);
            nt.mInputs.add(cl);
            validateCycle(nf, nf);
            return this;
        }

        public Builder addConnection(Type t, Script.KernelID from, Script.KernelID to) {
            Node nf = findNode(from);
            if (nf == null) {
                throw new RSInvalidStateException("From script not found.");
            }
            Node nt = findNode(to);
            if (nt == null) {
                throw new RSInvalidStateException("To script not found.");
            }
            ConnectLine cl = new ConnectLine(t, from, to);
            mLines.add(cl);
            nf.mOutputs.add(cl);
            nt.mInputs.add(cl);
            validateCycle(nf, nf);
            return this;
        }

        private boolean calcOrderRecurse(Node node, int depth) {
            node.mSeen = true;
            if (node.mOrder < depth) {
                node.mOrder = depth;
            }
            boolean ret = true;
            for (int i = 0; i < node.mOutputs.size(); i++) {
                ConnectLine link = node.mOutputs.get(i);
                Node child = link.mToF != null
                        ? findNode(link.mToF.mScript)
                        : findNode(link.mToK.mScript);
                if (child.mSeen) {
                    return false;
                }
                ret &= calcOrderRecurse(child, node.mOrder + 1);
            }
            node.mSeen = false;
            return ret;
        }

        private void sortNodes() {
            for (int i = 0; i < mNodes.size(); i++) {
                Node n = mNodes.get(i);
                if (n.mInputs.isEmpty()) {
                    for (int j = 0; j < mNodes.size(); j++) {
                        mNodes.get(j).mSeen = false;
                    }
                    if (!calcOrderRecurse(n, 1)) {
                        throw new RSInvalidStateException("Cycle detected in ScriptGroup");
                    }
                }
            }
            for (int i = 0; i < mNodes.size(); i++) {
                for (int j = i + 1; j < mNodes.size(); j++) {
                    if (mNodes.get(i).mOrder > mNodes.get(j).mOrder) {
                        Node tmp = mNodes.get(i);
                        mNodes.set(i, mNodes.get(j));
                        mNodes.set(j, tmp);
                    }
                }
            }
        }

        public ScriptGroup create() {
            if (mNodes.size() == 0) {
                throw new RSInvalidStateException("Empty script groups are not allowed");
            }
            for (int ct = 0; ct < mNodes.size(); ct++) {
                mNodes.get(ct).dagNumber = 0;
            }
            validateDAG();
            sortNodes();

            ArrayList<IO> inputs = new ArrayList<IO>();
            ArrayList<IO> outputs = new ArrayList<IO>();

            for (int ct = 0; ct < mNodes.size(); ct++) {
                Node n = mNodes.get(ct);
                for (int ct2 = 0; ct2 < n.mKernels.size(); ct2++) {
                    Script.KernelID kid = n.mKernels.get(ct2);
                    boolean hasInput = false;
                    for (int ct3 = 0; ct3 < n.mInputs.size(); ct3++) {
                        if (n.mInputs.get(ct3).mToK == kid) {
                            hasInput = true;
                        }
                    }
                    boolean hasOutput = false;
                    for (int ct3 = 0; ct3 < n.mOutputs.size(); ct3++) {
                        if (n.mOutputs.get(ct3).mFrom == kid) {
                            hasOutput = true;
                        }
                    }
                    if (!hasInput) {
                        inputs.add(new IO(kid));
                    }
                    if (!hasOutput) {
                        outputs.add(new IO(kid));
                    }
                }
            }
            if (inputs.size() != mKernelCount - mLines.size() && inputs.isEmpty()) {
                throw new RSRuntimeException("Count mismatch, should not happen.");
            }

            ScriptGroup sg = new ScriptGroup(0, mRS);
            sg.mOutputs = new IO[outputs.size()];
            for (int ct = 0; ct < outputs.size(); ct++) {
                sg.mOutputs[ct] = outputs.get(ct);
            }
            sg.mInputs = new IO[inputs.size()];
            for (int ct = 0; ct < inputs.size(); ct++) {
                sg.mInputs[ct] = inputs.get(ct);
            }
            sg.mNodes = new ArrayList<Node>(mNodes);
            return sg;
        }
    }

    public static final class Binding {
        private final Script.FieldID mField;
        private final Object mValue;

        public Binding(Script.FieldID field, Object value) {
            mField = field;
            mValue = value;
        }

        Script.FieldID getField() {
            return mField;
        }

        Object getValue() {
            return mValue;
        }
    }

    public static final class Builder2 {
        private final RenderScript mRS;
        private final List<Closure> mClosures = new ArrayList<Closure>();
        private final List<Input> mInputs = new ArrayList<Input>();

        public Builder2(RenderScript rs) {
            mRS = rs;
        }

        private Closure addKernelInternal(Script.KernelID k, Type returnType, Object[] args,
                                          Map<Script.FieldID, Object> globalBindings) {
            Closure c = new Closure(mRS, k, returnType, args, globalBindings);
            mClosures.add(c);
            return c;
        }

        private Closure addInvokeInternal(Script.InvokeID invoke, Object[] args,
                                          Map<Script.FieldID, Object> globalBindings) {
            Closure c = new Closure(mRS, invoke, args, globalBindings);
            mClosures.add(c);
            return c;
        }

        public Input addInput() {
            Input unbound = new Input();
            mInputs.add(unbound);
            return unbound;
        }

        public Closure addKernel(Script.KernelID k, Type returnType, Object... argsAndBindings) {
            ArrayList<Object> args = new ArrayList<Object>();
            Map<Script.FieldID, Object> bindingMap = new HashMap<Script.FieldID, Object>();
            if (!seperateArgsAndBindings(argsAndBindings, args, bindingMap)) {
                return null;
            }
            return addKernelInternal(k, returnType, args.toArray(), bindingMap);
        }

        public Closure addInvoke(Script.InvokeID invoke, Object... argsAndBindings) {
            ArrayList<Object> args = new ArrayList<Object>();
            Map<Script.FieldID, Object> bindingMap = new HashMap<Script.FieldID, Object>();
            if (!seperateArgsAndBindings(argsAndBindings, args, bindingMap)) {
                return null;
            }
            return addInvokeInternal(invoke, args.toArray(), bindingMap);
        }

        public ScriptGroup create(String name, Future... outputs) {
            if (name == null || name.isEmpty() || name.length() > 100
                    || !name.equals(name.replaceAll("[^a-zA-Z0-9-]", "_"))) {
                throw new RSIllegalArgumentException("invalid script group name");
            }
            return new ScriptGroup(mRS, name,
                    new ArrayList<Closure>(mClosures),
                    new ArrayList<Input>(mInputs),
                    outputs);
        }

        private boolean seperateArgsAndBindings(Object[] argsAndBindings,
                                                ArrayList<Object> args,
                                                Map<Script.FieldID, Object> bindingMap) {
            if (argsAndBindings == null) {
                return true;
            }
            int i = 0;
            for (; i < argsAndBindings.length; i++) {
                if (argsAndBindings[i] instanceof Binding) {
                    break;
                }
                args.add(argsAndBindings[i]);
            }
            for (; i < argsAndBindings.length; i++) {
                if (!(argsAndBindings[i] instanceof Binding)) {
                    return false;
                }
                Binding b = (Binding) argsAndBindings[i];
                bindingMap.put(b.getField(), b.getValue());
            }
            return true;
        }
    }

    public void destroy() {
        super.destroy();
        if (mClosures != null) {
            for (int i = 0; i < mClosures.size(); i++) {
                Closure c = mClosures.get(i);
                if (!c.isDestroyed()) {
                    c.destroy();
                }
            }
        }
    }
}
