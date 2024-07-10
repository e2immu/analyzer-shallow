package org.e2immu.analyzer.shallow.analyzer;

import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class TestRun {
    @Test
    public void test() throws IOException {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.e2immu.analyzer.shallow")).setLevel(Level.DEBUG);

        Run run = new Run();
        run.go(new String[]{"../e2immu-shallow-aapi/src/main/java/org/e2immu/analyzer/shallow/aapi"});
    }
}
