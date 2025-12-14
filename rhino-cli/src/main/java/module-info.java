module org.mozilla.rhino.cli {
    requires org.mozilla.rhino;
    requires org.mozilla.rhino.tools;
    requires org.jline;

    exports org.mozilla.javascript.cli;

    provides org.mozilla.javascript.tools.ConsoleProvider with
            org.mozilla.javascript.cli.JLineConsoleProvider;
}