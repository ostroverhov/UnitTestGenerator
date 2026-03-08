package com.example.unittestgenerator;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

import java.io.File;

/**
 * Extension for configuring the unit test generator plugin.
 */
public class UnitTestGeneratorExtension {

    private final Property<String> testLanguage;
    private final ListProperty<String> targetClasses;
    private final Property<String> testFramework;
    private final Property<String> model;
    private final Property<File> outputDir;

    public UnitTestGeneratorExtension(ObjectFactory objects) {
        this.testLanguage = objects.property(String.class);
        this.targetClasses = objects.listProperty(String.class);
        this.testFramework = objects.property(String.class);
        this.model = objects.property(String.class);
        this.outputDir = objects.property(File.class);
        this.testFramework.convention("junit5");
        this.model.convention("deepseek-chat");
    }

    /**
     * Language of generated tests: "java", "kotlin", etc. Passed to the prompt.
     */
    @Input
    public Property<String> getTestLanguage() {
        return testLanguage;
    }

    /**
     * Fully qualified class names to generate tests for.
     */
    @Input
    public ListProperty<String> getTargetClasses() {
        return targetClasses;
    }

    /**
     * Test framework for the prompt: "junit5", "junit4", "testng", etc.
     */
    @Input
    @Optional
    public Property<String> getTestFramework() {
        return testFramework;
    }

    /**
     * DeepSeek model name. Default: "deepseek-chat".
     */
    @Input
    @Optional
    public Property<String> getModel() {
        return model;
    }

    /**
     * Output directory for generated test files. If not set, uses project's test source set by language.
     */
    @Optional
    public Property<File> getOutputDir() {
        return outputDir;
    }
}
