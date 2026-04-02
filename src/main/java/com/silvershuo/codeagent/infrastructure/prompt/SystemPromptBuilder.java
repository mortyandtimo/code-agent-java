package com.silvershuo.codeagent.infrastructure.prompt;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class SystemPromptBuilder {

    public String build(String workingDirectory) {
        String template = loadTemplate();
        return template
                .replace("{{cwd}}", safe(workingDirectory))
                .replace("{{date}}", LocalDate.now().toString())
                .replace("{{platform}}", safe(System.getProperty("os.name") + " " + System.getProperty("os.arch")))
                .replace("{{shell}}", safe(System.getenv("ComSpec")))
                .replace("{{git_context}}", buildGitContext(workingDirectory));
    }

    private String loadTemplate() {
        try {
            ClassPathResource resource = new ClassPathResource("system-prompt.md");
            BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        } catch (Exception ex) {
            return "You are Code Agent Java. Use tools to complete software engineering tasks in the current working directory.";
        }
    }

    private String buildGitContext(String workingDirectory) {
        List<String> lines = new ArrayList<String>();
        appendGitCommand(lines, workingDirectory, "git rev-parse --abbrev-ref HEAD", "Git branch: ");
        appendGitCommand(lines, workingDirectory, "git log --oneline -5", "Recent commits:\n");
        appendGitCommand(lines, workingDirectory, "git status --short", "Git status:\n");
        if (lines.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line);
        }
        return builder.toString();
    }

    private void appendGitCommand(List<String> lines, String workingDirectory, String command, String prefix) {
        try {
            ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", command);
            builder.directory(new java.io.File(workingDirectory));
            builder.redirectErrorStream(true);
            Process process = builder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() > 0) {
                    output.append('\n');
                }
                output.append(line);
            }
            process.waitFor();
            if (process.exitValue() == 0 && output.length() > 0) {
                lines.add(prefix + output.toString());
            }
        } catch (Exception ignored) {
        }
    }

    private String safe(String value) {
        return value == null ? "unknown" : value;
    }
}
