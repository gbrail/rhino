package org.mozilla.javascript.benchmarks;

import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;

public class Runner {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: Runner <script file> [duration in seconds]");
            System.out.println("       If no duration, will run forever");
            System.exit(2);
        }

        Instant stopTime = null;
        if (args.length > 1) {
            stopTime = Instant.now().plusSeconds(Long.parseLong(args[1]));
        }

        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            cx.setOptimizationLevel(9);
            Scriptable root = cx.initStandardObjects();

            Script testScript;
            try (FileReader rdr = new FileReader(args[0])) {
                testScript = cx.compileReader(rdr, "test.js", 1, null);
            }

            int iterations = 0;
            Instant startTime = Instant.now();
            while (stopTime == null || stopTime.isAfter(Instant.now())) {
                testScript.exec(cx, root);
                iterations++;
            }
            Instant endTime = Instant.now();
            Duration elapsed = Duration.between(startTime, endTime);
            System.out.println(iterations + " executions in " + elapsed);
            double micros = elapsed.toNanos() / 1000.0;
            double microsPerOp = micros / (double) iterations;
            System.out.println("  " + microsPerOp + " microseconds / iteration");
        }
    }
}
