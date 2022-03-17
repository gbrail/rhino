package org.mozilla.javascript.ast;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.mozilla.javascript.Token;

/** The representation of a class definition. */
public class ClassNode extends ScriptNode {
    private Name functionName;
    private int functionType;
    private final HashMap<String, FunctionNode> functions = new HashMap<>();

    {
        type = Token.CLASS;
    }

    public ClassNode() {}

    public ClassNode(int pos, Name name) {
        super(pos);
        setFunctionName(name);
    }

    public Name getFunctionName() {
        return functionName;
    }

    public void setFunctionName(Name name) {
        functionName = name;
        if (name != null) name.setParent(this);
    }

    public int getFunctionType() {
        return functionType;
    }

    public void setFunctionType(int type) {
        functionType = type;
    }

    public void addClassFunction(String name, FunctionNode node) {
        functions.put(name, node);
    }

    public FunctionNode getClassFunction(String name) {
        return functions.get(name);
    }

    public Set<Map.Entry<String, FunctionNode>> getClassFunctions() {
      return functions.entrySet();
    }
}
