package org.mozilla.javascript.cli;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.tools.Console;

import java.io.IOException;
import java.io.PrintStream;

public class JLineConsole implements Console {
    private final Terminal terminal;
    private final LineReader reader;

    JLineConsole(Terminal t, Scriptable scope) {
        this.terminal = t;
        this.reader = LineReaderBuilder.
                builder().
                terminal(t).
                completer(new JavascriptCompleter(scope)).
                build();
    }

    @Override
    public String readLine(String prompt) throws IOException {
        return reader.readLine(prompt);
    }

    @Override
    public String readLine() throws IOException {
        return reader.readLine();
    }

    @Override
    public void print(String msg) {
        terminal.writer().print(msg);
    }

    @Override
    public void println(String msg) {
        terminal.writer().println(msg);
    }

    @Override
    public void println() {
        terminal.writer().println();
    }

    @Override
    public void flush() {
        terminal.writer().flush();
    }

    @Override
    public PrintStream getOut() {
        return new PrintStream(terminal.output());
    }

    @Override
    public PrintStream getErr() {
        return new PrintStream(terminal.output());
    }
}
