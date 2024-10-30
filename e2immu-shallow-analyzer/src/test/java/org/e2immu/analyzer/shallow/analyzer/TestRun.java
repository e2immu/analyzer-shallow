package org.e2immu.analyzer.shallow.analyzer;

import ch.qos.logback.classic.Level;
import org.e2immu.language.cst.api.analysis.Message;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class TestRun {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestRun.class);

    @Test
    public void test() throws IOException {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.e2immu.analyzer.shallow")).setLevel(Level.DEBUG);

        Run run = new Run();
        List<Message> messages = run.go(new String[]{"../e2immu-shallow-aapi/src/main/java/org/e2immu/analyzer/shallow/aapi"});
        LOGGER.info("Have {} message(s)", messages.size());
        messages.forEach(m -> {
            LOGGER.info("{} {}: {}", m.level(), m.info(), m.message());
        });
    }
}
