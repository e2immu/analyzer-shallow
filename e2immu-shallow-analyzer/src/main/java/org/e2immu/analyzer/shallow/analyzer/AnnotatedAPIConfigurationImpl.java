package org.e2immu.analyzer.shallow.analyzer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class AnnotatedAPIConfigurationImpl implements AnnotatedAPIConfiguration {
    public static final String DO_NOT_READ_ANNOTATED_API = "-";
    public static final String DO_NOT_READ_ANALYZED_ANNOTATED_API = "-";

    private final List<String> analyzedAnnotatedApiDirs;
    private final List<String> annotatedApiSourcePackages;
    private final String analyzedAnnotatedApiTargetDirectory;
    private final String annotatedApiTargetDirectory;

    private AnnotatedAPIConfigurationImpl(List<String> analyzedAnnotatedApiDirs,
                                          List<String> annotatedApiSourcePackages,
                                          String analyzedAnnotatedApiTargetDirectory,
                                          String annotatedApiTargetDirectory) {
        this.analyzedAnnotatedApiDirs = analyzedAnnotatedApiDirs;
        this.annotatedApiSourcePackages = annotatedApiSourcePackages;
        this.analyzedAnnotatedApiTargetDirectory = analyzedAnnotatedApiTargetDirectory;
        this.annotatedApiTargetDirectory = annotatedApiTargetDirectory;
    }

    @Override
    public List<String> analyzedAnnotatedApiDirs() {
        return analyzedAnnotatedApiDirs;
    }

    @Override
    public List<String> annotatedApiSourcePackages() {
        return annotatedApiSourcePackages;
    }

    @Override
    public String analyzedAnnotatedApiTargetDirectory() {
        return analyzedAnnotatedApiTargetDirectory;
    }

    @Override
    public String annotatedApiTargetDirectory() {
        return annotatedApiTargetDirectory;
    }

    public static class Builder {

        private final List<String> analyzedAnnotatedApiDirs = new ArrayList<>();
        private final List<String> annotatedApiSourcePackages = new ArrayList<>();
        private String analyzedAnnotatedApiTargetDirectory;
        private String annotatedApiTargetDirectory;

        public Builder setAnalyzedAnnotatedApiTargetDirectory(String analyzedAnnotatedApiTargetDirectory) {
            this.analyzedAnnotatedApiTargetDirectory = analyzedAnnotatedApiTargetDirectory;
            return this;
        }

        public Builder addAnalyzedAnnotatedApiDirs(String... analyzedAnnotatedApiDirs) {
            this.analyzedAnnotatedApiDirs.addAll(Arrays.asList(analyzedAnnotatedApiDirs));
            return this;
        }

        public Builder addAnnotatedApiSourcePackages(String... annotatedApiSourcePackages) {
            this.annotatedApiSourcePackages.addAll(Arrays.asList(annotatedApiSourcePackages));
            return this;
        }

        public Builder setAnnotatedApiTargetDirectory(String annotatedApiTargetDirectory) {
            this.annotatedApiTargetDirectory = annotatedApiTargetDirectory;
            return this;
        }

        public AnnotatedAPIConfiguration build() {
            return new AnnotatedAPIConfigurationImpl(List.copyOf(analyzedAnnotatedApiDirs),
                    List.copyOf(annotatedApiSourcePackages), analyzedAnnotatedApiTargetDirectory,
                    annotatedApiTargetDirectory);
        }
    }
}
