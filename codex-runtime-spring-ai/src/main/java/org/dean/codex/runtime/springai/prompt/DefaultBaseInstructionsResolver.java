package org.dean.codex.runtime.springai.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class DefaultBaseInstructionsResolver implements BaseInstructionsResolver {

    private static final String DEFAULT_TEMPLATE_RESOURCE = "prompts/base-instructions.md";

    private final Path workspaceRoot;
    private final String baseInstructionsTextOverride;
    private final String baseInstructionsFileOverride;
    private final String defaultTemplate;

    public DefaultBaseInstructionsResolver(Path workspaceRoot) {
        this(workspaceRoot, null, null);
    }

    public DefaultBaseInstructionsResolver(Path workspaceRoot,
                                           String baseInstructionsTextOverride,
                                           String baseInstructionsFileOverride) {
        this(workspaceRoot, baseInstructionsTextOverride, baseInstructionsFileOverride, loadResource(DEFAULT_TEMPLATE_RESOURCE));
    }

    DefaultBaseInstructionsResolver(Path workspaceRoot,
                                    String baseInstructionsTextOverride,
                                    String baseInstructionsFileOverride,
                                    String defaultTemplate) {
        this.workspaceRoot = PromptOverrideSupport.normalizeWorkspaceRoot(workspaceRoot);
        this.baseInstructionsTextOverride = baseInstructionsTextOverride;
        this.baseInstructionsFileOverride = baseInstructionsFileOverride;
        this.defaultTemplate = PromptOverrideSupport.normalize(defaultTemplate);
    }

    @Override
    public String resolveBaseInstructions() {
        String resolvedOverride = PromptOverrideSupport.resolveOverrideText(
                workspaceRoot,
                baseInstructionsTextOverride,
                baseInstructionsFileOverride);
        if (!resolvedOverride.isBlank()) {
            return resolvedOverride;
        }
        return PromptOverrideSupport.renderWorkspaceRoot(workspaceRoot, defaultTemplate);
    }

    private static String loadResource(String resourcePath) {
        try (InputStream stream = DefaultBaseInstructionsResolver.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Missing prompt resource: " + resourcePath);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException exception) {
            throw new IllegalStateException("Failed to load prompt resource: " + resourcePath, exception);
        }
    }
}
