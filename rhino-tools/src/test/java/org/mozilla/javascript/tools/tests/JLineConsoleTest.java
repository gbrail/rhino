package org.mozilla.javascript.tools.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.tools.shell.JLineConsole;

/**
 * JLine only flushes the terminal writer while the terminal is active (during an interactive
 * readLine). When a script runs without a TTY the terminal is never activated, so JLineConsole must
 * flush its own output or it is silently lost.
 */
public class JLineConsoleTest {

    private static JLineConsole newConsole(ByteArrayOutputStream out) throws Exception {
        Terminal terminal =
                TerminalBuilder.builder()
                        .system(false)
                        .dumb(true)
                        .streams(new ByteArrayInputStream(new byte[0]), out)
                        .build();
        return new JLineConsole(terminal);
    }

    /** The line terminator as written through a JLine terminal, e.g. "\n" or "\r\n". */
    private static String lineSeparator() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JLineConsole console = newConsole(out)) {
            console.println();
        }
        return out.toString();
    }

    @Test
    public void printWritesImmediately() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JLineConsole console = newConsole(out)) {
            console.print("hello");
            assertEquals("hello", out.toString());
        }
    }

    @Test
    public void printlnWritesLine() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JLineConsole console = newConsole(out)) {
            console.println("world");
            assertEquals("world" + lineSeparator(), out.toString());
        }
    }

    @Test
    public void barePrintlnWritesOnlyLineSeparator() throws Exception {
        String separator = lineSeparator();
        assertTrue(
                separator.equals("\n") || separator.equals("\r\n"),
                "unexpected line separator: " + separator);
    }

    @Test
    public void getErrIsSystemErr() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JLineConsole console = newConsole(out)) {
            assertSame(System.err, console.getErr());
        }
    }

    @Test
    public void flushWritesPendingOutput() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JLineConsole console = newConsole(out)) {
            console.print("a");
            console.print("b");
            assertEquals("ab", out.toString());
            console.flush();
            assertEquals("ab", out.toString());
        }
    }

    @Test
    public void printWhileReadingUsesPrintAbove() throws Exception {
        PipedOutputStream inWrite = new PipedOutputStream();
        PipedInputStream inRead = new PipedInputStream(inWrite);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Terminal terminal =
                TerminalBuilder.builder().system(false).dumb(true).streams(inRead, out).build();
        try (JLineConsole console = new JLineConsole(terminal)) {
            Thread t = new Thread(() -> console.readLine("prompt> "));
            t.start();

            // Wait until the reader is actively reading
            while (!out.toString().contains("prompt> ")) {
                Thread.sleep(10);
            }

            console.println("async-msg");

            // Complete readLine
            inWrite.write("line\n".getBytes(StandardCharsets.UTF_8));
            inWrite.flush();
            t.join(2000);
            assertFalse(t.isAlive());

            assertTrue(out.toString().contains("async-msg"));
            assertTrue(out.toString().contains("prompt> "));
        }
    }
}
