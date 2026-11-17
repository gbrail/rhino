package org.mozilla.javascript.tools.shell;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.mozilla.javascript.tools.Console;

public class JLineConsole implements Console {
    private final Terminal terminal;
    private final LineReader reader;
    private final StringBuilder lineBuffer = new StringBuilder();

    public JLineConsole(Terminal t) {
        this.terminal = t;
        var parser = new DefaultParser();
        parser.setEscapeChars(null);
        this.reader = LineReaderBuilder.builder().terminal(t).parser(parser).build();
    }

    @Override
    public void close() throws IOException {
        terminal.close();
    }

    @Override
    public String getImplementation() {
        return "JLine " + terminal.getType();
    }

    @Override
    public String readLine(String prompt) {
        try {
            return reader.readLine(prompt);
        } catch (UserInterruptException | EndOfFileException e) {
            // This will cause the "main" to cleanly exit
            return null;
        }
    }

    @Override
    public String readLine() {
        try {
            return reader.readLine();
        } catch (UserInterruptException | EndOfFileException e) {
            return null;
        }
    }

    // JLine only flushes the terminal writer when the terminal is active (during an
    // interactive readLine), so flush explicitly or output is lost in script mode.

    @Override
    public synchronized void print(String msg) {
        if (reader.isReading()) {
            lineBuffer.append(msg);
            int newlineIdx;
            while ((newlineIdx = lineBuffer.indexOf("\n")) != -1) {
                String line = lineBuffer.substring(0, newlineIdx);
                if (line.endsWith("\r")) {
                    line = line.substring(0, line.length() - 1);
                }
                lineBuffer.delete(0, newlineIdx + 1);
                reader.printAbove(line);
            }
        } else {
            PrintWriter w = terminal.writer();
            w.print(msg);
            w.flush();
        }
    }

    @Override
    public synchronized void println(String msg) {
        if (reader.isReading()) {
            lineBuffer.append(msg);
            String full = lineBuffer.toString();
            lineBuffer.setLength(0);
            reader.printAbove(full);
        } else {
            PrintWriter w = terminal.writer();
            w.println(msg);
            w.flush();
        }
    }

    @Override
    public synchronized void println() {
        if (reader.isReading()) {
            String full = lineBuffer.toString();
            lineBuffer.setLength(0);
            reader.printAbove(full);
        } else {
            PrintWriter w = terminal.writer();
            w.println();
            w.flush();
        }
    }

    @Override
    public synchronized void flush() {
        if (reader.isReading()) {
            if (lineBuffer.length() > 0) {
                String full = lineBuffer.toString();
                lineBuffer.setLength(0);
                reader.printAbove(full);
            }
        } else {
            terminal.writer().flush();
        }
    }

    @Override
    public PrintStream getOut() {
        return new PrintStream(terminal.output());
    }

    @Override
    public PrintStream getErr() {
        // Errors (e.g. from the error reporter) must go to stderr, not the terminal.
        return System.err;
    }
}
