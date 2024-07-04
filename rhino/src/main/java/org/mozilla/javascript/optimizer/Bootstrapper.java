package org.mozilla.javascript.optimizer;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.regex.Pattern;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.DynamicLinker;
import jdk.dynalink.DynamicLinkerFactory;
import jdk.dynalink.NamedOperation;
import jdk.dynalink.NamespaceOperation;
import jdk.dynalink.Operation;
import jdk.dynalink.StandardNamespace;
import jdk.dynalink.StandardOperation;
import jdk.dynalink.linker.support.CompositeTypeBasedGuardingDynamicLinker;
import jdk.dynalink.support.ChainedCallSite;
import org.mozilla.classfile.ByteCode;
import org.mozilla.classfile.ClassFileWriter;
import org.mozilla.javascript.ScriptRuntime;

public class Bootstrapper {
    private static final Pattern SEPARATOR = Pattern.compile(":");

    public static final ClassFileWriter.MHandle BOOTSTRAP_HANDLE =
            new ClassFileWriter.MHandle(
                    ByteCode.MH_INVOKESTATIC,
                    "org.mozilla.javascript.optimizer.Bootstrapper",
                    "bootstrap",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;"
                            + "Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;");

    private static final DynamicLinker linker;

    static {
        DynamicLinkerFactory factory = new DynamicLinkerFactory();
        factory.setPrioritizedLinkers(
                new CompositeTypeBasedGuardingDynamicLinker(Arrays.asList(new ScriptableLinker())),
                new DefaultLinker());
        linker = factory.createLinker();
    }

    public static CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType mType)
            throws NoSuchMethodException, IllegalAccessException {
        Operation op = parseOperation(name);
        return linker.link(new ChainedCallSite(new CallSiteDescriptor(lookup, op, mType)));
    }

    private static Operation parseOperation(String name) throws NoSuchMethodException {
        String[] tokens = SEPARATOR.split(name);
        String namespaceName = getNameSegment(tokens, name, 0);
        String opName = getNameSegment(tokens, name, 1);
        if ("PROP".equals(namespaceName)) {
            switch (opName) {
                case "GET":
                    return StandardOperation.GET
                            .withNamespace(StandardNamespace.PROPERTY)
                            .named(getNameSegment(tokens, name, 2));
                case "GETNOWARN":
                    return RhinoOperation.GETNOWARN
                            .withNamespace(StandardNamespace.PROPERTY)
                            .named(getNameSegment(tokens, name, 2));
                case "SET":
                    return StandardOperation.SET
                            .withNamespace(StandardNamespace.PROPERTY)
                            .named(getNameSegment(tokens, name, 2));
                default:
                    throw new NoSuchMethodException(name);
            }
        } else if ("NAME".equals(namespaceName)) {
            switch (opName) {
                case "GET":
                    return StandardOperation.GET
                            .withNamespace(RhinoNamespace.NAME)
                            .named(getNameSegment(tokens, name, 2));
                case "SET":
                    return StandardOperation.SET
                            .withNamespace(RhinoNamespace.NAME)
                            .named(getNameSegment(tokens, name, 2));
                case "SETSTRICT":
                    return RhinoOperation.SETSTRICT
                            .withNamespace(RhinoNamespace.NAME)
                            .named(getNameSegment(tokens, name, 2));
                case "BIND":
                    return RhinoOperation.BIND
                            .withNamespace(RhinoNamespace.NAME)
                            .named(getNameSegment(tokens, name, 2));
                case "INCRPRE":
                    return RhinoOperation.PREINCREMENT
                            .withNamespace(RhinoNamespace.NAME)
                            .named(getNameSegment(tokens, name, 2));
                case "DECRPRE":
                    return RhinoOperation.PREDECREMENT
                        .withNamespace(RhinoNamespace.NAME)
                        .named(getNameSegment(tokens, name, 2));
                case "INCRPOST":
                    return RhinoOperation.POSTINCREMENT
                            .withNamespace(RhinoNamespace.NAME)
                            .named(getNameSegment(tokens, name, 2));
                case "DECRPOST":
                    return RhinoOperation.POSTDECREMENT
                        .withNamespace(RhinoNamespace.NAME)
                        .named(getNameSegment(tokens, name, 2));
                default:
                    throw new NoSuchMethodException(name);
            }
        } else {
            throw new NoSuchMethodException(name);
        }
    }

    private static String getNameSegment(String[] segments, String name, int pos)
            throws NoSuchMethodException {
        if (pos >= segments.length) {
            throw new NoSuchMethodException(name);
        }
        return segments[pos];
    }
}
