package dev.demo.jobboard.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "mcp")
public class McpConfig {

    /**
     * Path to the MCP server executable or Python script
     */
    private String command = "../mcp-jobboard/.venv/bin/python";

    /**
     * Working directory for MCP server execution
     */
    private String workingDirectory = "../mcp-jobboard";

    /**
     * Main script file for MCP server
     */
    private String mainScript = "main.py";

    /**
     * Timeout for MCP operations in seconds
     */
    private int timeoutSeconds = 30;

    /**
     * Whether to enable MCP direct execution (bypass Temporal)
     */
    private boolean enableDirectExecution = true;

    // Getters and setters
    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public String getMainScript() {
        return mainScript;
    }

    public void setMainScript(String mainScript) {
        this.mainScript = mainScript;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isEnableDirectExecution() {
        return enableDirectExecution;
    }

    public void setEnableDirectExecution(boolean enableDirectExecution) {
        this.enableDirectExecution = enableDirectExecution;
    }

    /**
     * Get the full command path for MCP execution
     */
    public String getFullCommand() {
        return command;
    }

    /**
     * Get the resolved working directory path
     */
    public String getResolvedWorkingDirectory() {
        return java.nio.file.Path.of(System.getProperty("user.dir"))
                .resolve(workingDirectory)
                .normalize()
                .toString();
    }
}
