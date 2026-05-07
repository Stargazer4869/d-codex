package org.dean.codex.cli.appserver;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "codex.cli.diagnostics")
public class CodexCliDiagnosticsProperties {

    private boolean enabled = true;
    private String directory = "logs";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory == null ? "" : directory;
    }

    public Path resolvedDirectory() {
        if (directory == null || directory.isBlank()) {
            return Path.of("logs").toAbsolutePath().normalize();
        }
        return Path.of(directory).toAbsolutePath().normalize();
    }
}
