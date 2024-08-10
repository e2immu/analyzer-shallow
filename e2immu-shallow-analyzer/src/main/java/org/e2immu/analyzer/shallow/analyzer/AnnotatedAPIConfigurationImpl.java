package org.e2immu.analyzer.shallow.analyzer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class AnnotatedAPIConfigurationImpl implements AnnotatedAPIConfiguration {
    public static final String DO_NOT_READ_ANNOTATED_API = "-";
    public static final String DO_NOT_READ_ANALYZED_ANNOTATED_API = "-";

    // use case 1
    private final List<String> analyzedAnnotatedApiDirs;

    // use case 2
    private final String analyzedAnnotatedApiTargetDirectory;
    private final List<String> annotatedApiSourcePackages;

    // use case 3
    private final List<String> annotatedApiPackages;
    private final String annotatedApiTargetDirectory;
    private final String annotatedApiTargetPackage;

    private AnnotatedAPIConfigurationImpl(List<String> analyzedAnnotatedApiDirs,
                                          List<String> annotatedApiSourcePackages,
                                          String analyzedAnnotatedApiTargetDirectory,
                                          List<String> annotatedApiPackages,
                                          String annotatedApiTargetDirectory,
                                          String annotatedApiTargetPackage) {
        this.analyzedAnnotatedApiDirs = analyzedAnnotatedApiDirs;
        this.annotatedApiSourcePackages = annotatedApiSourcePackages;
        this.analyzedAnnotatedApiTargetDirectory = analyzedAnnotatedApiTargetDirectory;
        this.annotatedApiTargetDirectory = annotatedApiTargetDirectory;
        this.annotatedApiTargetPackage = annotatedApiTargetPackage;
        this.annotatedApiPackages = annotatedApiPackages;
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

    @Override
    public String annotatedApiTargetPackage() {
        return annotatedApiTargetPackage;
    }

    @Override
    public List<String> annotatedApiPackages() {
        return annotatedApiPackages;
    }

    @Override
    public String toString() {
        return "AnnotatedAPIConfigurationImpl{" +
               "analyzedAnnotatedApiDirs=" + analyzedAnnotatedApiDirs +
               ", analyzedAnnotatedApiTargetDirectory='" + analyzedAnnotatedApiTargetDirectory + '\'' +
               ", annotatedApiSourcePackages=" + annotatedApiSourcePackages +
               ", annotatedApiPackages=" + annotatedApiPackages +
               ", annotatedApiTargetDirectory='" + annotatedApiTargetDirectory + '\'' +
               ", annotatedApiTargetPackage='" + annotatedApiTargetPackage + '\'' +
               '}';
    }

    public static class Builder {

        private final List<String> analyzedAnnotatedApiDirs = new ArrayList<>();
        private final List<String> annotatedApiSourcePackages = new ArrayList<>();
        private final List<String> annotatedApiPackages = new ArrayList<>();
        private String analyzedAnnotatedApiTargetDirectory;
        private String annotatedApiTargetDirectory;
        private String annotatedApiTargetPackage;

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

        public Builder addAnnotatedApiPackages(String... annotatedApiPackages) {
            this.annotatedApiPackages.addAll(Arrays.asList(annotatedApiPackages));
            return this;
        }

        public Builder setAnnotatedApiTargetDirectory(String annotatedApiTargetDirectory) {
            this.annotatedApiTargetDirectory = annotatedApiTargetDirectory;
            return this;
        }

        public Builder setAnnotatedApiTargetPackage(String annotatedApiTargetPackage) {
            this.annotatedApiTargetPackage = annotatedApiTargetPackage;
            return this;
        }

        public AnnotatedAPIConfiguration build() {
            return new AnnotatedAPIConfigurationImpl(List.copyOf(analyzedAnnotatedApiDirs),
                    List.copyOf(annotatedApiSourcePackages), analyzedAnnotatedApiTargetDirectory,
                    List.copyOf(annotatedApiPackages), annotatedApiTargetDirectory, annotatedApiTargetPackage);
        }
    }
}
