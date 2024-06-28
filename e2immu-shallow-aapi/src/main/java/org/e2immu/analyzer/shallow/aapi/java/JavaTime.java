package org.e2immu.analyzer.shallow.aapi.java;

import org.e2immu.annotation.Independent;

import java.time.Duration;

public class JavaTime {

    public static final String PACKAGE_NAME = "java.time";

    interface Duration$ {

        @Independent
        Duration ofMillis(long millis);
    }
}
