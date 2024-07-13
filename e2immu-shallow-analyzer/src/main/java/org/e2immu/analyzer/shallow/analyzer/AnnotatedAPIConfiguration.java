package org.e2immu.analyzer.shallow.analyzer;

import java.util.List;

public interface AnnotatedAPIConfiguration {

    List<String> analyzedAnnotatedApiDirs();

    List<String> annotatedApiSourcePackages();

    String analyzedAnnotatedApiTargetDirectory();

    String annotatedApiTargetDirectory();
}
