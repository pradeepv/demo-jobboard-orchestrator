package dev.demo.jobboard.orchestrator.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mcp")
public class McpConfig {

    /**
     * Path to the MCP server executable (e.g., Python interpreter).
     * Example: ../mcp-jobboard/.venv/bin/python
     */
    private String command = "../mcp-jobboard/.venv/bin/python";

    /**
     * Working directory where the MCP main script resides.
     * Example: ../mcp-jobboard
     */
    private String workingDirectory = "../mcp-jobboard";

    /**
     * Main script filename to execute.
     * Example: main.py
     */
    private String mainScript = "main.py";

    /**
     * Timeout for MCP operations in seconds.
     */
    private int timeoutSeconds = 30;

    /**
     * Whether to enable MCP direct execution (shell out to Python) vs stub.
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
     * Full command path used to invoke the interpreter/executable.
     */
    public String getFullCommand() {
        return command;
    }

    /**
     * Resolve working directory relative to current user.dir if not absolute.
     */
    public String getResolvedWorkingDirectory() {
        Path wd = Path.of(workingDirectory);
        if (wd.isAbsolute()) {
            return wd.normalize().toString();
        }
        return Path.of(System.getProperty("user.dir"))
                .resolve(wd)
                .normalize()
                .toString();
    }
}