# Unit Test Generator Gradle Plugin

Gradle plugin that generates unit tests for specified classes using the DeepSeek API.

## Requirements

- Java 11+
- Gradle 8.x (or create wrapper with `gradle wrapper`)

## Build

```bash
gradle build
```

To publish to the local Maven repository (default: `build/local-repo`):

```bash
gradle publish
```

## Usage

Apply the plugin in your project and configure the extension:

```groovy
plugins {
    id 'com.example.unit-test-generator' version '1.0.0'
}

unitTestGenerator {
    testLanguage = 'java'   // or 'kotlin'
    targetClasses = ['com.myapp.ServiceA', 'com.myapp.UtilB']
    testFramework = 'junit5'  // optional, default: junit5
    model = 'deepseek-chat'    // optional, default: deepseek-chat
    outputDir = file('src/test/java')  // optional, default: src/test/java or src/test/kotlin by language
}
```

Run the task:

```bash
export DEEPSEEK_API_KEY=your-api-key
gradle generateUnitTests
```

The API key is read **only** from the `DEEPSEEK_API_KEY` environment variable.

## Configuration

| Parameter       | Type        | Description |
|----------------|-------------|-------------|
| testLanguage   | String      | `"java"` or `"kotlin"` |
| targetClasses  | List<String>| Fully qualified class names to cover |
| testFramework  | String      | e.g. `"junit5"`, `"junit4"`, `"testng"` (optional, default: junit5) |
| model          | String      | DeepSeek model (optional, default: deepseek-chat) |
| outputDir      | File        | Output directory for generated tests (optional) |

Source files are resolved from `src/main/java/` (`.java` or `.kt` by FQN). Generated tests are written to the configured output directory (default: `src/test/java` or `src/test/kotlin`).
