package org.mozilla.javascript.optimizer;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.regex.Pattern;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.DynamicLinker;
import jdk.dynalink.DynamicLinkerFactory;
import jdk.dynalink.Operation;
import jdk.dynalink.StandardNamespace;
import jdk.dynalink.StandardOperation;
import jdk.dynalink.support.ChainedCallSite;
import org.mozilla.classfile.ByteCode;
import org.mozilla.classfile.ClassFileWriter;

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
        factory.setPrioritizedLinkers(/*new ShapeAwareLinker(), */ new DefaultLinker());
        linker = factory.createLinker();
    }

    public static CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType mType)
            throws NoSuchMethodException, IllegalAccessException {
        Operation op = parseOperation(name);
        return linker.link(new ChainedCallSite(new CallSiteDescriptor(lookup, op, mType)));
    }

    private static Operation parseOperation(String name) throws NoSuchMethodException {
        String[] tokens = SEPARATOR.split(name, -1);
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
            }
        } else if ("NAME".equals(namespaceName)) {
            switch (opName) {
                case "BIND":
                    return RhinoOperation.BIND
                            .withNamespace(RhinoNamespace.NAME)
                            .named(getNameSegment(tokens, name, 2));
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
            }
        }

        // Fall through to no match
        throw new NoSuchMethodException(name);
    }

    private static String getNameSegment(String[] segments, String name, int pos)
            throws NoSuchMethodException {
        if (pos >= segments.length) {
            throw new NoSuchMethodException(name);
        }
        return segments[pos];
    }
}