package com.example.unittestgenerator;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Task that generates unit tests for target classes using DeepSeek API.
 */
@CacheableTask
public class GenerateUnitTestsTask extends DefaultTask {

    private static final int MAX_TOKENS = 4096;
    private static final Pattern CODE_BLOCK = Pattern.compile("```(?:\\w+)?\\s*\\n([\\s\\S]*?)```");

    private final Property<String> testLanguage = getProject().getObjects().property(String.class);
    private final ListProperty<String> targetClasses = getProject().getObjects().listProperty(String.class);
    private final Property<String> testFramework = getProject().getObjects().property(String.class);
    private final Property<String> model = getProject().getObjects().property(String.class);
    private final Property<File> outputDir = getProject().getObjects().property(File.class);

    @Input
    public Property<String> getTestLanguage() {
        return testLanguage;
    }

    @Input
    public ListProperty<String> getTargetClasses() {
        return targetClasses;
    }

    @Input
    public Property<String> getTestFramework() {
        return testFramework;
    }

    @Input
    public Property<String> getModel() {
        return model;
    }

    public Property<File> getOutputDir() {
        return outputDir;
    }

    /**
     * Resolves target class FQNs to source files. Used for task inputs.
     */
    public List<File> getSourceFiles() {
        if (!targetClasses.isPresent()) {
            return Collections.emptyList();
        }
        List<File> files = new ArrayList<>();
        for (String fqn : targetClasses.get()) {
            File f = findSourceFile(fqn);
            if (f != null) files.add(f);
        }
        return files;
    }

    /**
     * Effective output directory: from extension or default src/test/java|kotlin by language.
     */
    public File getEffectiveOutputDir() {
        if (outputDir.isPresent()) {
            return outputDir.get();
        }
        String lang = testLanguage.getOrElse("java");
        String subdir = "kotlin".equalsIgnoreCase(lang) ? "kotlin" : "java";
        return getProject().file("src/test/" + subdir);
    }

    private final DirectoryProperty outputDirectory = getProject().getObjects().directoryProperty();

    @OutputDirectory
    public DirectoryProperty getOutputDirectory() {
        return outputDirectory;
    }

    @TaskAction
    public void generate() throws IOException {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "DEEPSEEK_API_KEY environment variable is not set. Set it to your DeepSeek API key.");
        }

        List<String> classes = targetClasses.get();
        if (classes == null || classes.isEmpty()) {
            getLogger().info("No target classes configured, skipping.");
            return;
        }

        File outDir = getEffectiveOutputDir();
        outDir.mkdirs();

        String lang = testLanguage.getOrElse("java");
        String framework = testFramework.getOrElse("junit5");
        String modelName = model.getOrElse("deepseek-chat");

        getLogger().lifecycle("Generating unit tests for {} class(es) (language={}, framework={}, model={})",
                classes.size(), lang, framework, modelName);
        getLogger().debug("Output directory: {}", outDir.getAbsolutePath());

        DeepSeekClient client = new DeepSeekClient(apiKey);
        String systemPrompt = buildSystemPrompt(lang, framework);

        int generated = 0;
        for (String fqn : classes) {
            getLogger().info("Generating tests for {}", fqn);

            File sourceFile = findSourceFile(fqn);
            if (sourceFile == null) {
                getLogger().warn("Source file not found for class: {} (searched src/main/java)", fqn);
                continue;
            }
            getLogger().debug("Resolved source file: {} -> {}", fqn, sourceFile.getAbsolutePath());

            String sourceCode = Files.readString(sourceFile.toPath(), StandardCharsets.UTF_8);
            String userPrompt = "Generate unit tests for the following class. Output only the test code in a single markdown code block.\n\n" + sourceCode;

            List<DeepSeekClient.Message> messages = new ArrayList<>();
            messages.add(new DeepSeekClient.Message("system", systemPrompt));
            messages.add(new DeepSeekClient.Message("user", userPrompt));

            try {
                getLogger().debug("Calling DeepSeek API for {} (model={}, maxTokens={})", fqn, modelName, MAX_TOKENS);
                long startMs = System.currentTimeMillis();

                String response = client.chatCompletion(modelName, messages, MAX_TOKENS);

                long elapsedMs = System.currentTimeMillis() - startMs;
                List<String> codeBlocks = parseCodeBlocks(response);
                getLogger().debug("API response for {}: {} ms, {} code block(s) parsed", fqn, elapsedMs, codeBlocks.size());

                if (codeBlocks.isEmpty()) {
                    getLogger().warn("No code block in response for {}", fqn);
                    continue;
                }
                String testCode = codeBlocks.get(0).trim();
                String testFileName = deriveTestFileName(fqn);
                File testFile = new File(outDir, testFileName);
                testFile.getParentFile().mkdirs();
                Files.writeString(testFile.toPath(), testCode, StandardCharsets.UTF_8);
                generated++;
                getLogger().lifecycle("Generated: {}", testFile.getAbsolutePath());
            } catch (DeepSeekClient.DeepSeekApiException e) {
                getLogger().error("DeepSeek API failed for {}: {}", fqn, e.getMessage());
                throw new RuntimeException("DeepSeek API failed for " + fqn + ": " + e.getMessage(), e);
            }
        }

        getLogger().info("Done. Generated {} test file(s) into {}", generated, outDir.getAbsolutePath());
    }

    private static String buildSystemPrompt(String language, String framework) {
        return "You are a unit test generator. Generate tests in " + language + " using " + framework + ". "
                + "Output only the test class code in a single markdown code block, no explanations.";
    }

    private File findSourceFile(String fqn) {
        String path = fqn.replace('.', File.separatorChar);
        File javaFile = getProject().file("src/main/java/" + path + ".java");
        if (javaFile.exists()) return javaFile;
        File ktFile = getProject().file("src/main/java/" + path + ".kt");
        if (ktFile.exists()) return ktFile;
        return null;
    }

    private static List<String> parseCodeBlocks(String content) {
        List<String> blocks = new ArrayList<>();
        Matcher m = CODE_BLOCK.matcher(content);
        while (m.find()) {
            blocks.add(m.group(1));
        }
        return blocks;
    }

    private String deriveTestFileName(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        String simpleName = lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
        String pkg = lastDot >= 0 ? fqn.substring(0, lastDot).replace('.', File.separatorChar) : "";
        String suffix = "kotlin".equalsIgnoreCase(testLanguage.getOrElse("java")) ? "Test.kt" : "Test.java";
        if (pkg.isEmpty()) {
            return simpleName + suffix;
        }
        return pkg + File.separator + simpleName + suffix;
    }
}
