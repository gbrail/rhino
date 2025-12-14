package org.mozilla.javascript.cli.tests;

import org.jline.reader.Candidate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.cli.JavascriptCompleter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompleterTest {
    private static Context cx;
    private static Scriptable rootScope;
    private Scriptable testScope;
    private JavascriptCompleter completer;

    @BeforeAll
    public static void setup() {
        cx = Context.enter();
        rootScope = cx.initStandardObjects();
    }

    @AfterAll
    public static void cleanup() {
        Context.exit();
    }

    @BeforeEach
    public void init() {
        testScope = cx.newObject(rootScope);
        var foo = cx.newObject(testScope);
        ScriptableObject.defineProperty(foo, "AAA", 1, 0);
        ScriptableObject.defineProperty(foo, "BBB", 2, 0);
        ScriptableObject.defineProperty(testScope, "one", 1, 0);
        ScriptableObject.defineProperty(testScope, "two", 2, 0);
        ScriptableObject.defineProperty(testScope, "three", 3, 0);
        ScriptableObject.defineProperty(testScope, "foo", foo, 0);
        completer = new JavascriptCompleter(testScope);
    }

    public static Object[][] successfulCompletions() {
        return new Object[][] {
                {"", new String[]{"one", "two", "three", "foo"}},
                {"f", new String[]{"one", "two", "three", "foo"}},
                {"fo", new String[]{"one", "two", "three", "foo"}},
                {"foo", new String[]{"one", "two", "three", "foo"}},
                {"foo.", new String[]{"foo.AAA","foo.BBB"}},
                {"foo.o", new String[]{"foo.AAA","foo.BBB"}},
                {"foo.on", new String[]{"foo.AAA","foo.BBB"}},
                {"foo.one", new String[]{"foo.AAA","foo.BBB"}},
        };
    };

    @ParameterizedTest
    @MethodSource("successfulCompletions")
    public void testCompletionSuccess(String word, String[] expected) {
        var candidates = new ArrayList<Candidate>();
        completer.completeWord(word, candidates);
        matchCompletions(candidates, expected);
    }

    private void matchCompletions(List<Candidate> candidates, String[] matches) {
        var missingMatches = new HashSet<String>();
        for (var m : matches) {
            missingMatches.add(m);
        }
        for (var c : candidates) {
            assertTrue(missingMatches.remove(c.value()));
        }
        assertTrue(missingMatches.isEmpty(), "not all candidates were found");
    }
}
