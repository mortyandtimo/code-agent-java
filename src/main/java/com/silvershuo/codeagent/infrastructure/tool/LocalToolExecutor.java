package com.silvershuo.codeagent.infrastructure.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvershuo.codeagent.domain.tool.service.ToolExecutor;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class LocalToolExecutor implements ToolExecutor {

    private static final int SMALL_FILE_LINE_LIMIT = 1200;
    private static final long SMALL_FILE_BYTE_LIMIT = 64 * 1024L;
    private static final int SHELL_TIMEOUT_SECONDS = 30;
    private static final int SHELL_OUTPUT_LINE_LIMIT = 300;
    private static final Pattern[] DANGEROUS_SHELL_PATTERNS = new Pattern[]{
            Pattern.compile("(^|[\\s&(])del(\\s|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|[\\s&(])erase(\\s|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|[\\s&(])rm(\\s|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|[\\s&(])rd(\\s|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|[\\s&(])rmdir(\\s|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bremove-item\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bshutdown\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\breboot\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\btaskkill\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bgit\\s+(push|reset|clean)\\b", Pattern.CASE_INSENSITIVE)
    };
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String describeAvailableTools() {
        return "read_file, write_file, edit_file, list_files, delete_file, run_shell";
    }

    @Override
    public String buildToolDefinitionsJson() {
        return "["
                + "{\"type\":\"function\",\"function\":{\"name\":\"read_file\",\"description\":\"Read a file from the working directory\",\"parameters\":{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},\"required\":[\"path\"]}}},"
                + "{\"type\":\"function\",\"function\":{\"name\":\"write_file\",\"description\":\"Write content to a file\",\"parameters\":{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"},\"content\":{\"type\":\"string\"}},\"required\":[\"path\",\"content\"]}}},"
                + "{\"type\":\"function\",\"function\":{\"name\":\"edit_file\",\"description\":\"Replace old text with new text in a file\",\"parameters\":{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"},\"old\":{\"type\":\"string\"},\"new\":{\"type\":\"string\"}},\"required\":[\"path\",\"old\",\"new\"]}}},"
                + "{\"type\":\"function\",\"function\":{\"name\":\"list_files\",\"description\":\"List files under a path\",\"parameters\":{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},\"required\":[\"path\"]}}},"
                + "{\"type\":\"function\",\"function\":{\"name\":\"delete_file\",\"description\":\"Delete a single file at an exact path\",\"parameters\":{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},\"required\":[\"path\"]}}},"
                + "{\"type\":\"function\",\"function\":{\"name\":\"run_shell\",\"description\":\"Run a shell command inside the working directory\",\"parameters\":{\"type\":\"object\",\"properties\":{\"command\":{\"type\":\"string\"}},\"required\":[\"command\"]}}}"
                + "]";
    }

    @Override
    public String confirmationMessage(String toolName, String argumentsJson, String workingDirectory) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson == null || argumentsJson.trim().isEmpty() ? "{}" : argumentsJson);
            if ("run_shell".equals(toolName)) {
                String command = args.path("command").asText("");
                if (needsShellConfirmation(command)) {
                    return command;
                }
            }
            if ("write_file".equals(toolName)) {
                Path path = resolvePath(args.path("path").asText(""), workingDirectory);
                if (!Files.exists(path)) {
                    return "write new file: " + path;
                }
            }
            if ("delete_file".equals(toolName)) {
                Path path = resolvePath(args.path("path").asText(""), workingDirectory);
                return "delete file: " + path;
            }
            if ("edit_file".equals(toolName)) {
                Path path = resolvePath(args.path("path").asText(""), workingDirectory);
                if (!Files.exists(path)) {
                    return "edit non-existent file: " + path;
                }
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public String execute(String toolName, String argumentsJson, String workingDirectory) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson == null || argumentsJson.trim().isEmpty() ? "{}" : argumentsJson);
            if ("read_file".equals(toolName)) {
                return readFile(args.path("path").asText(""), workingDirectory);
            }
            if ("write_file".equals(toolName)) {
                return writeFile(args.path("path").asText(""), args.path("content").asText(""), workingDirectory);
            }
            if ("edit_file".equals(toolName)) {
                return editFile(args.path("path").asText(""), args.path("old").asText(""), args.path("new").asText(""), workingDirectory);
            }
            if ("list_files".equals(toolName)) {
                return listFiles(args.path("path").asText("."), workingDirectory);
            }
            if ("delete_file".equals(toolName)) {
                return deleteFile(args.path("path").asText(""), workingDirectory);
            }
            if ("run_shell".equals(toolName)) {
                return runShell(args.path("command").asText(""), workingDirectory);
            }
            return "Unsupported tool: " + toolName;
        } catch (Exception ex) {
            return "Tool execution failed: " + ex.getMessage();
        }
    }

    @Override
    public String recoverMissingToolName(String toolCallId, String argumentsJson, String toolResult, String workingDirectory) {
        if (toolResult == null) {
            return "";
        }
        if (toolResult.startsWith("read_file failed:") || toolResult.startsWith("File not found:") || toolResult.contains(" | ")) {
            return "read_file";
        }
        if (toolResult.startsWith("Wrote file:")) {
            return "write_file";
        }
        if (toolResult.startsWith("Edited file:")) {
            return "edit_file";
        }
        if (toolResult.startsWith("Deleted file:") || toolResult.startsWith("File not found, nothing deleted:")) {
            return "delete_file";
        }
        if (toolResult.startsWith("Path not found:") || toolResult.contains("\\") || toolResult.contains("/")) {
            try {
                JsonNode args = objectMapper.readTree(argumentsJson == null || argumentsJson.trim().isEmpty() ? "{}" : argumentsJson);
                if (args.has("path")) {
                    return "list_files";
                }
            } catch (Exception ignored) {
            }
        }
        if (toolResult.startsWith("Command ") || toolResult.startsWith("run_shell failed:")) {
            return "run_shell";
        }
        return "";
    }

    private String readFile(String pathValue, String workingDirectory) {
        try {
            Path path = resolvePath(pathValue, workingDirectory);
            if (!Files.exists(path)) {
                return "File not found: " + path;
            }
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            long bytes = Files.size(path);
            boolean smallFile = bytes <= SMALL_FILE_BYTE_LIMIT && lines.size() <= SMALL_FILE_LINE_LIMIT;
            int limit = smallFile ? lines.size() : Math.min(lines.size(), 800);
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < limit; i++) {
                builder.append(i + 1).append(" | ").append(lines.get(i)).append('\n');
            }
            if (!smallFile) {
                builder.append("... FILE TRUNCATED, remaining lines: ").append(lines.size() - limit).append(". If needed, call read_file again on a narrower target file or ask for another focused read.\n");
            }
            return builder.toString();
        } catch (Exception ex) {
            return "read_file failed: " + ex.getMessage();
        }
    }

    private String writeFile(String pathValue, String content, String workingDirectory) {
        try {
            Path path = resolvePath(pathValue, workingDirectory);
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.write(path, content.getBytes(StandardCharsets.UTF_8));
            return "Wrote file: " + path;
        } catch (Exception ex) {
            return "write_file failed: " + ex.getMessage();
        }
    }

    private String editFile(String pathValue, String oldValue, String newValue, String workingDirectory) {
        try {
            Path path = resolvePath(pathValue, workingDirectory);
            if (!Files.exists(path)) {
                return "File not found: " + path;
            }
            String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            if (!content.contains(oldValue)) {
                return "edit_file failed: old text not found";
            }
            content = content.replace(oldValue, newValue);
            Files.write(path, content.getBytes(StandardCharsets.UTF_8));
            return "Edited file: " + path;
        } catch (Exception ex) {
            return "edit_file failed: " + ex.getMessage();
        }
    }

    private String listFiles(String pathValue, String workingDirectory) {
        try {
            Path base = resolvePath(pathValue == null || pathValue.isEmpty() ? "." : pathValue, workingDirectory);
            if (!Files.exists(base)) {
                return "Path not found: " + base;
            }
            List<String> results = new ArrayList<String>();
            try (Stream<Path> stream = Files.walk(base, 2)) {
                stream.filter(path -> !shouldSkip(path))
                        .limit(80)
                        .forEach(path -> results.add(path.toString()));
            }
            return String.join("\n", results);
        } catch (Exception ex) {
            return "list_files failed: " + ex.getMessage();
        }
    }

    private String deleteFile(String pathValue, String workingDirectory) {
        try {
            Path path = resolvePath(pathValue, workingDirectory);
            if (!Files.exists(path)) {
                return "File not found, nothing deleted: " + path;
            }
            if (Files.isDirectory(path)) {
                return "delete_file failed: target is a directory: " + path;
            }
            Files.delete(path);
            return "Deleted file: " + path;
        } catch (Exception ex) {
            return "delete_file failed: " + ex.getMessage();
        }
    }

    private boolean shouldSkip(Path path) {
        String value = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return value.contains("/.m2repo")
                || value.contains("/target")
                || value.contains("/.git")
                || value.contains("/node_modules")
                || value.contains("/outputs/runtime");
    }

    private String runShell(String command, String workingDirectory) {
        if (command == null || command.trim().isEmpty()) {
            return "Empty shell command";
        }
        try {
            ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", command);
            builder.directory(new File(workingDirectory));
            builder.redirectErrorStream(true);
            Process process = builder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder();
            String line;
            int count = 0;
            long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(SHELL_TIMEOUT_SECONDS);
            while (System.currentTimeMillis() < deadline && count < SHELL_OUTPUT_LINE_LIMIT) {
                while (reader.ready() && (line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                    count++;
                    if (count >= SHELL_OUTPUT_LINE_LIMIT) {
                        break;
                    }
                }
                if (!process.isAlive()) {
                    break;
                }
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    process.destroyForcibly();
                    return "run_shell failed: interrupted";
                }
            }

            if (process.isAlive()) {
                process.destroyForcibly();
                if (output.length() > 0) {
                    return "Command timed out after " + SHELL_TIMEOUT_SECONDS + " seconds; partial output captured\n" + output.toString();
                }
                return "run_shell failed: command timed out after " + SHELL_TIMEOUT_SECONDS + " seconds\n" + output.toString();
            }

            while ((line = reader.readLine()) != null && count < SHELL_OUTPUT_LINE_LIMIT) {
                output.append(line).append('\n');
                count++;
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                return "Command succeeded with exit code 0\n" + output.toString();
            }
            return "Command failed with exit code " + exitCode + "\n" + output.toString();
        } catch (Exception ex) {
            return "run_shell failed: " + ex.getMessage();
        }
    }

    private Path resolvePath(String pathValue, String workingDirectory) {
        Path path = Paths.get(pathValue);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return Paths.get(workingDirectory).resolve(path).normalize();
    }

    private boolean needsShellConfirmation(String command) {
        if (command == null) {
            return false;
        }
        for (Pattern pattern : DANGEROUS_SHELL_PATTERNS) {
            if (pattern.matcher(command).find()) {
                return true;
            }
        }
        return false;
    }
}
