package org.mozilla.javascript;

/**
 * This class specializes LambdaFunction to make it easier and more efficient for functions that
 * take one argument.
 */
public class LambdaFunction2 extends LambdaFunction {
    /**
     * Create a new function. The new object will have the Function prototype and no parent. The
     * caller is responsible for binding this object to the appropriate scope.
     *
     * @param scope scope of the calling context
     * @param name name of the function
     * @param target an object that implements the function in Java. Since Callable11 is a
     *     single-function interface this will typically be implemented as a lambda.
     */
    public LambdaFunction2(Scriptable scope, String name, Callable2 target) {
        super(scope, name, 1, new CallAdapter(target));
    }

    /** Create a new built-in function, with no name, and no default prototype. */
    public LambdaFunction2(Scriptable scope, Callable2 target) {
        super(scope, 1, new CallAdapter(target));
    }

    private static final class CallAdapter implements Callable {
        private final Callable2 target;

        CallAdapter(Callable2 target) {
            this.target = target;
        }

        @Override
        public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
            Object arg1 = Undefined.instance;
            if (args != null && args.length >= 1) {
                arg1 = args[0];
            }
            Object arg2 = Undefined.instance;
            if (args != null && args.length >= 2) {
                arg2 = args[1];
            }
            return target.call(cx, scope, thisObj, arg1, arg2);
        }

        @Override
        public Object call0(Context cx, Scriptable scope, Scriptable thisObj) {
            return target.call(cx, scope, thisObj, Undefined.instance, Undefined.instance);
        }

        @Override
        public Object call1(Context cx, Scriptable scope, Scriptable thisObj, Object arg) {
            return target.call(cx, scope, thisObj, arg, Undefined.instance);
        }

        @Override
        public Object call2(
                Context cx, Scriptable scope, Scriptable thisObj, Object arg1, Object arg2) {
            return target.call(cx, scope, thisObj, arg1, arg2);
        }
    }
}
