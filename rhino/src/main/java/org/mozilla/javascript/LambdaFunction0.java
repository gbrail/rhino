package org.mozilla.javascript;

/**
 * This class specializes LambdaFunction to make it easier and more efficient for functions that
 * take no arguments.
 */
public class LambdaFunction0 extends LambdaFunction {
    /**
     * Create a new function. The new object will have the Function prototype and no parent. The
     * caller is responsible for binding this object to the appropriate scope.
     *
     * @param scope scope of the calling context
     * @param name name of the function
     * @param target an object that implements the function in Java. Since Callable11 is a
     *     single-function interface this will typically be implemented as a lambda.
     */
    public LambdaFunction0(Scriptable scope, String name, Callable0 target) {
        super(scope, name, 0, new CallAdapter(target));
    }

    /** Create a new built-in function, with no name, and no default prototype. */
    public LambdaFunction0(Scriptable scope, Callable0 target) {
        super(scope, 0, new CallAdapter(target));
    }

    private static final class CallAdapter implements Callable {
        private final Callable0 target;

        CallAdapter(Callable0 target) {
            this.target = target;
        }

        @Override
        public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
            return target.call(cx, scope, thisObj);
        }

        @Override
        public Object call0(Context cx, Scriptable scope, Scriptable thisObj) {
            return target.call(cx, scope, thisObj);
        }

        @Override
        public Object call1(Context cx, Scriptable scope, Scriptable thisObj, Object arg) {
            return target.call(cx, scope, thisObj);
        }

        @Override
        public Object call2(
                Context cx, Scriptable scope, Scriptable thisObj, Object arg1, Object arg2) {
            return target.call(cx, scope, thisObj);
        }
    }
}
