package org.mozilla.javascript.tools;

import java.io.IOException;
import java.io.PrintStream;

public interface Console {
    String readLine(String prompt) throws IOException;

    String readLine() throws IOException;

    void print(String msg);

    void println(String msg);

    void println();

    void flush();

    PrintStream getOut();

    PrintStream getErr();
}
