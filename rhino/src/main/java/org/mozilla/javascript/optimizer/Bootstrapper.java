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
import jdk.dynalink.linker.GuardingDynamicLinker;
import jdk.dynalink.support.SimpleRelinkableCallSite;
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
        GuardingDynamicLinker defaultLinker = new DefaultLinker();
        DynamicLinkerFactory factory = new DynamicLinkerFactory();
        factory.setPrioritizedLinker(defaultLinker);
        linker = factory.createLinker();
    }

    public static CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType mType)
            throws NoSuchMethodException {
        Operation op = parseOperation(name);
        return linker.link(new SimpleRelinkableCallSite(new CallSiteDescriptor(lookup, op, mType)));
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
