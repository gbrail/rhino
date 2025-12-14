package org.mozilla.javascript.cli;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.util.List;
import java.util.regex.Pattern;

public class JavascriptCompleter implements Completer {
    private static final Pattern DOT = Pattern.compile("\\.");

    private final Scriptable scope;

    public JavascriptCompleter(Scriptable scope) {
        this.scope = scope;
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        completeWord(line.line(), candidates);
    }

    public void completeWord(String line, List<Candidate> candidates) {
        // Go back and find the last identifier
        int m = line.length()-1;
        while (m > 0) {
            char c = line.charAt(m);
            if (!Character.isJavaIdentifierPart(c) && c != '.') break;
            m--;
        }
        String start = m > 0 ?  line.substring(0, m+1) : "";
        // Split last word into identifiers
        String end = m >= 0 ? line.substring(m) : "";
        String[] parts = DOT.split(end, -1);
        ScriptableObject last = (ScriptableObject)scope;
        // Iterate through the parts, but skip the last, to find the object to search
        StringBuilder prefix = new StringBuilder(start);
        for (int i = 0; i < parts.length-1; i++) {
            String p = parts[i];
            if (p.isEmpty()) {
                continue;
            }
            var next = last.get(p, last);
            if (next != Scriptable.NOT_FOUND && next instanceof ScriptableObject) {
                last = (ScriptableObject)next;
            } else {
                // No completions
                return;
            }
            prefix.append(p);
            prefix.append('.');
        }
        String addPrefix = prefix.toString();
        // Get possible completions
        Object[] ids = last.getAllIds();
        for (Object id : ids) {
            if (id instanceof String) {
                String val = addPrefix + id;
                candidates.add(new Candidate(val, val, "", "", "", "", false));
            }
        }
    }

    /* Old complete
    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        // Starting from "cursor" at the end of the buffer, look backward
        // and collect a list of identifiers separated by (possibly zero)
        // dots. Then look up each identifier in turn until getting to the
        // last, presumably incomplete fragment. Then enumerate all the
        // properties of the last object and find any that have the
        // fragment as a prefix and return those for autocompletion.
        int m = line.wordCursor()-1;
        while (m >= 0) {
            char c = line.word().charAt(m);
            if (!Character.isJavaIdentifierPart(c) && c != '.') break;
            m--;
        }
        String namesAndDots = line.line().substring(m + 1, line.cursor());
        String[] names = namesAndDots.split("\\.", -1);
        Scriptable obj = scope;
        for (int i = 0; i < names.length - 1; i++) {
            Object val = obj.get(names[i], scope);
            if (val instanceof Scriptable) {
                obj = (Scriptable) val;
            } else {
                return; // no matches
            }
        }
        Object[] ids =
                (obj instanceof ScriptableObject)
                        ? ((ScriptableObject) obj).getAllIds()
                        : obj.getIds();
        String lastPart = names[names.length - 1];
        for (int i = 0; i < ids.length; i++) {
            if (!(ids[i] instanceof String)) continue;
            String id = (String) ids[i];
            if (id.startsWith(lastPart)) {
                if (obj.get(id, obj) instanceof Function) {
                    id += "(";
                }
                candidates.add(new Candidate(id));
            }
        }
    }*/
}
