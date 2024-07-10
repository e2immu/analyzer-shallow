package org.e2immu.analyzer.shallow.analyzer;

import org.junit.jupiter.api.Test;

import java.io.IOException;

public class TestRun {
    @Test
    public void test() throws IOException {
        Run run = new Run();
        run.go(new String[]{"../e2immu-shallow-aapi/src/main/java/org/e2immu/analyzer/shallow/aapi"});
    }
}
