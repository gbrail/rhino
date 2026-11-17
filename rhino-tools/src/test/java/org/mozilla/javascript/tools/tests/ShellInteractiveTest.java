package org.mozilla.javascript.tools.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.tools.shell.Global;
import org.mozilla.javascript.tools.shell.Main;

public class ShellInteractiveTest {
    private Global oldGlobal;

    @BeforeEach
    public void setup() {
        oldGlobal = Main.global;
        Main.global = new Global();
    }

    @AfterEach
    public void teardown() {
        Main.global.cleanup();
        Main.global = oldGlobal;
    }

    @Test
    public void timerFiresDuringInteractiveSession() throws Exception {
        PipedOutputStream inWrite = new PipedOutputStream();
        PipedInputStream inRead = new PipedInputStream(inWrite);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Main.getGlobal().setIn(inRead);
        Main.getGlobal().setOut(new PrintStream(out));

        Thread execThread =
                new Thread(
                        () -> {
                            Main.exec(new String[0]);
                        },
                        "shell-exec");
        execThread.start();

        inWrite.write(
                "setTimeout(() => print('ASYNC_TIMER_FIRED'), 50);\n"
                        .getBytes(StandardCharsets.UTF_8));
        inWrite.flush();

        // Wait for timer to execute while interactive session is open
        long deadline = System.currentTimeMillis() + 3000;
        while (!out.toString().contains("ASYNC_TIMER_FIRED")
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }

        assertTrue(out.toString().contains("ASYNC_TIMER_FIRED"));

        // Close stdin (EOF)
        inWrite.close();
        execThread.join(2000);
        assertFalse(execThread.isAlive());
    }

    @Test
    public void recurringTimerCancelledOnEof() throws Exception {
        PipedOutputStream inWrite = new PipedOutputStream();
        PipedInputStream inRead = new PipedInputStream(inWrite);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Main.getGlobal().setIn(inRead);
        Main.getGlobal().setOut(new PrintStream(out));

        Thread execThread =
                new Thread(
                        () -> {
                            Main.exec(new String[0]);
                        },
                        "shell-exec-recurring");
        execThread.start();

        inWrite.write(
                "function tick() { print('TICK'); setTimeout(tick, 20); }\ntick();\n"
                        .getBytes(StandardCharsets.UTF_8));
        inWrite.flush();

        long deadline = System.currentTimeMillis() + 3000;
        while (!out.toString().contains("TICK") && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(out.toString().contains("TICK"));

        // Close stdin (EOF). The shell must exit immediately and not hang forever.
        inWrite.close();
        execThread.join(2000);
        assertFalse(execThread.isAlive());
    }

    @Test
    public void multiLineInteractiveExecution() throws Exception {
        PipedOutputStream inWrite = new PipedOutputStream();
        PipedInputStream inRead = new PipedInputStream(inWrite);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Main.getGlobal().setIn(inRead);
        Main.getGlobal().setOut(new PrintStream(out));

        Thread execThread =
                new Thread(
                        () -> {
                            Main.exec(new String[0]);
                        },
                        "shell-exec-multiline");
        execThread.start();

        inWrite.write("function multiply(a, b) {\n".getBytes(StandardCharsets.UTF_8));
        inWrite.write("return a * b;\n".getBytes(StandardCharsets.UTF_8));
        inWrite.write("}\n".getBytes(StandardCharsets.UTF_8));
        inWrite.write("multiply(6, 7);\n".getBytes(StandardCharsets.UTF_8));
        inWrite.flush();

        long deadline = System.currentTimeMillis() + 3000;
        while (!out.toString().contains("42") && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(out.toString().contains("42"));

        inWrite.close();
        execThread.join(2000);
        assertFalse(execThread.isAlive());
    }
}
