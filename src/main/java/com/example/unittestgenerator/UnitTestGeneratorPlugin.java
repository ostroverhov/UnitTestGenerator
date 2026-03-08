package com.example.unittestgenerator;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Gradle plugin that generates unit tests using DeepSeek API.
 */
public class UnitTestGeneratorPlugin implements Plugin<Project> {

    public static final String EXTENSION_NAME = "unitTestGenerator";
    public static final String TASK_NAME = "generateUnitTests";

    @Override
    public void apply(Project project) {
        project.getLogger().debug("Applying unit-test-generator plugin, registering extension '{}' and task '{}'",
                EXTENSION_NAME, TASK_NAME);

        UnitTestGeneratorExtension extension = project.getExtensions()
                .create(EXTENSION_NAME, UnitTestGeneratorExtension.class);

        project.getTasks().register(TASK_NAME, GenerateUnitTestsTask.class, task -> {
            task.getTestLanguage().set(extension.getTestLanguage());
            task.getTargetClasses().set(extension.getTargetClasses());
            task.getTestFramework().set(extension.getTestFramework());
            task.getModel().set(extension.getModel());
            task.getOutputDir().set(extension.getOutputDir());
            task.getOutputDirectory().fileValue(project.provider(() -> task.getEffectiveOutputDir()));
            task.getInputs().files(project.provider(() -> task.getSourceFiles()));
        });
    }
}
