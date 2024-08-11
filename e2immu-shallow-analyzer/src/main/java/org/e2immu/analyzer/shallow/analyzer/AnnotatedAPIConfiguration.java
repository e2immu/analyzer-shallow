package org.e2immu.analyzer.shallow.analyzer;

import java.util.List;

public interface AnnotatedAPIConfiguration {

    // use case 1

    List<String> analyzedAnnotatedApiDirs();

    // use case 2

    String analyzedAnnotatedApiTargetDirectory();

    // use case 3

    List<String> annotatedApiPackages();

    String annotatedApiTargetDirectory();

    String annotatedApiTargetPackage();
}
