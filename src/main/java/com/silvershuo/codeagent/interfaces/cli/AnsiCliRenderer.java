package com.silvershuo.codeagent.interfaces.cli;

import org.jline.reader.LineReader;

public class AnsiCliRenderer implements CliRenderer {

    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";
    private static final String GRAY = "\u001B[90m";

    private LineReader lineReader;
    private boolean printedAssistantText;

    public void setLineReader(LineReader lineReader) {
        this.lineReader = lineReader;
    }

    @Override
    public void resetTurn() {
        this.printedAssistantText = false;
    }

    @Override
    public void printWelcome(String cwd) {
        System.out.println();
        System.out.println(BOLD + CYAN + "  Mini Code Agent" + RESET + GRAY + " -- Java coding assistant\n" + RESET);
        System.out.println(GRAY + "  Type your request and press Enter." + RESET);
        System.out.println(GRAY + "  cwd: " + cwd + RESET);
        System.out.println(GRAY + "  Commands: /new /clear /resume <taskId> /tasks /help /shell <cmd>" + RESET);
        System.out.println(GRAY + "  Shortcuts: press / for command guide, press ESC twice to exit, use !<cmd> for local shell\n" + RESET);
    }

    @Override
    public String promptText() {
        return BOLD + GREEN + "\n> " + RESET;
    }

    @Override
    public void printAssistantText(String text) {
        if (text != null && !text.isEmpty()) {
            this.printedAssistantText = true;
            System.out.print(text);
        }
    }

    @Override
    public void printToolCall(String name, String summary) {
        String icon = toolIcon(name);
        String suffix = summary == null || summary.trim().isEmpty() ? "" : (" " + summary);
        System.out.println("\n  " + YELLOW + icon + " " + name + RESET + GRAY + suffix + RESET);
    }

    @Override
    public void printToolResult(String name, String summary) {
        if (summary == null || summary.trim().isEmpty()) {
            return;
        }
        String[] lines = summary.replace("\r", "").split("\n");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(DIM).append("  ").append(lines[i]).append(RESET);
        }
        System.out.println(builder.toString());
    }

    @Override
    public void printRetry(int attempt, int maxAttempts, String reason) {
        System.out.println("\n  " + YELLOW + "Retry " + attempt + "/" + maxAttempts + RESET + GRAY + ": " + reason + RESET);
    }

    @Override
    public void printInfo(String message) {
        System.out.println("\n  " + CYAN + message + RESET);
    }

    @Override
    public void printError(String message) {
        System.out.println("\n  " + RED + "Error: " + message + RESET);
    }

    @Override
    public void printDivider() {
        System.out.println();
        System.out.println(GRAY + "  " + "--------------------------------------------------" + RESET);
    }

    @Override
    public String commandGuideText() {
        return GRAY
                + "  Available commands:\n"
                + RESET
                + "  " + CYAN + "/new" + RESET + GRAY + "               start a fresh conversation\n" + RESET
                + "  " + CYAN + "/resume <taskId>" + RESET + GRAY + "   continue a previous task\n" + RESET
                + "  " + CYAN + "/tasks" + RESET + GRAY + "             show recent tasks\n" + RESET
                + "  " + CYAN + "/shell <cmd>" + RESET + GRAY + "      run a local shell command directly\n" + RESET
                + "  " + CYAN + "!<cmd>" + RESET + GRAY + "             shorthand for direct local shell command\n" + RESET
                + "  " + CYAN + "/help" + RESET + GRAY + "              show this guide\n" + RESET
                + "  " + CYAN + "/exit" + RESET + GRAY + "              exit the CLI" + RESET;
    }

    @Override
    public String infoText(String message) {
        return "  " + CYAN + message + RESET;
    }

    @Override
    public boolean confirm(String message) {
        try {
            String prompt = "\n  " + YELLOW + "Confirmation required: " + RESET + message + "\n  Allow? (y/n): ";
            String line;
            if (lineReader != null) {
                line = lineReader.readLine(prompt);
            } else {
                System.out.print(prompt);
                byte[] buffer = new byte[16];
                int read = System.in.read(buffer);
                line = read <= 0 ? null : new String(buffer, 0, read);
            }
            return line != null && line.trim().toLowerCase().startsWith("y");
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public boolean hasPrintedAssistantText() {
        return printedAssistantText;
    }

    private String toolIcon(String name) {
        if ("read_file".equals(name)) {
            return "📖";
        }
        if ("write_file".equals(name)) {
            return "✍";
        }
        if ("edit_file".equals(name)) {
            return "🔧";
        }
        if ("list_files".equals(name)) {
            return "📁";
        }
        if ("run_shell".equals(name)) {
            return "💻";
        }
        return "🔨";
    }
}
