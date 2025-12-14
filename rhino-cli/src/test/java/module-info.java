module org.mozilla.rhino.cli.tests {
    requires org.mozilla.rhino;
    requires org.mozilla.rhino.tools;
    requires org.mozilla.rhino.cli;
    requires org.junit.jupiter.api;
    requires junit;
    requires org.jline;
    requires org.junit.jupiter.params;

    exports org.mozilla.javascript.cli.tests;
}