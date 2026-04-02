package com.silvershuo.codeagent.interfaces.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvershuo.codeagent.application.command.ContinueTaskCommand;
import com.silvershuo.codeagent.application.command.CreateTaskCommand;
import com.silvershuo.codeagent.application.command.ResumeTaskCommand;
import com.silvershuo.codeagent.application.service.AgentApplicationService;
import com.silvershuo.codeagent.domain.tool.service.ToolExecutor;
import com.silvershuo.codeagent.interfaces.dto.TaskListItemResponse;
import com.silvershuo.codeagent.interfaces.dto.TaskResponse;
import org.jline.keymap.KeyMap;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Reference;
import org.jline.reader.ParsedLine;
import org.jline.reader.UserInterruptException;
import org.jline.reader.Widget;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;
import org.jline.widget.AutosuggestionWidgets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AgentCliRunner implements CommandLineRunner {

    private static final long ESC_EXIT_WINDOW_MS = 1500L;
    private static final int KEY_ENTER = 13;
    private static final int KEY_ESCAPE = 27;
    private static final int KEY_WINDOWS_PREFIX_0 = 0;
    private static final int KEY_WINDOWS_PREFIX_224 = 224;
    private static final long MENU_KEY_TIMEOUT_MILLIS = 180L;
    private static final int KEY_ARROW_UP = -1001;
    private static final int KEY_ARROW_DOWN = -1002;
    private static final List<String> COMMAND_SUGGESTIONS = Arrays.asList(
            "/new",
            "/clear",
            "/resume",
            "/tasks",
            "/help",
            "/shell",
            "/exit"
    );

    private final AgentApplicationService applicationService;
    private final ToolExecutor toolExecutor;
    private final boolean enabled;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private CliRenderer renderer;
    private long lastEscPressedAt;

    public AgentCliRunner(AgentApplicationService applicationService,
                          ToolExecutor toolExecutor,
                          @Value("${code-agent.cli.enabled:true}") boolean enabled) {
        this.applicationService = applicationService;
        this.toolExecutor = toolExecutor;
        this.enabled = enabled;
    }

    @Override
    public void run(String... args) {
        if (!enabled || args == null || args.length == 0) {
            return;
        }
        configureConsoleOutput();
        if ("run".equalsIgnoreCase(args[0])) {
            String prompt = null;
            String cwd = null;
            for (int i = 1; i < args.length - 1; i++) {
                if ("--prompt".equals(args[i])) {
                    prompt = args[i + 1];
                }
                if ("--cwd".equals(args[i])) {
                    cwd = args[i + 1];
                }
            }
            if (prompt == null || cwd == null) {
                return;
            }
            TaskResponse response = applicationService.createTaskAndRun(new CreateTaskCommand("CLI Task", prompt, cwd));
            System.out.println("Task " + response.getTaskId() + " => " + response.getStatus());
            System.out.println("FAILED".equalsIgnoreCase(response.getStatus()) ? response.getFailureReason() : response.getFinalResult());
            System.exit(0);
        }
        if ("resume".equalsIgnoreCase(args[0]) && args.length >= 2) {
            Long taskId = Long.valueOf(args[1]);
            TaskResponse response = applicationService.resumeTask(new ResumeTaskCommand(taskId));
            System.out.println("Resumed task " + response.getTaskId() + " => " + response.getStatus());
            System.out.println("FAILED".equalsIgnoreCase(response.getStatus()) ? response.getFailureReason() : response.getFinalResult());
            System.exit(0);
        }
        if ("chat".equalsIgnoreCase(args[0])) {
            String cwd = null;
            for (int i = 1; i < args.length - 1; i++) {
                if ("--cwd".equals(args[i])) {
                    cwd = args[i + 1];
                }
            }
            if (cwd == null || cwd.trim().isEmpty()) {
                System.out.println("Usage: chat --cwd <working-directory>");
                System.exit(1);
            }
            runInteractiveChat(cwd);
            System.exit(0);
        }
    }

    private void configureConsoleOutput() {
        try {
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8"));
            System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, "UTF-8"));
        } catch (Exception ignored) {
        }
    }

    private void runInteractiveChat(String cwd) {
        Terminal terminal = null;
        AnsiCliRenderer ansiRenderer = new AnsiCliRenderer();
        renderer = ansiRenderer;
        CliRenderContext.set(renderer);
        Long currentTaskId = null;
        try {
            terminal = createInteractiveTerminal();
            LineReader lineReader = createLineReader(terminal, ansiRenderer);
            ansiRenderer.setLineReader(lineReader);
            renderer.printWelcome(cwd);
            while (true) {
                String line;
                try {
                    line = lineReader.readLine(renderer.promptText());
                    lastEscPressedAt = 0L;
                } catch (UserInterruptException ex) {
                    renderer.printInfo("Interrupted. Press ESC twice or type /exit to quit.");
                    continue;
                } catch (EndOfFileException ex) {
                    renderer.printInfo("Bye!");
                    break;
                }

                if (line == null) {
                    break;
                }

                String input = line.trim();
                if (input.isEmpty()) {
                    continue;
                }

                if ("/".equals(input) || "/help".equalsIgnoreCase(input)) {
                    System.out.println();
                    System.out.println(renderer.commandGuideText());
                    continue;
                }
                if ("/exit".equalsIgnoreCase(input) || "/quit".equalsIgnoreCase(input)) {
                    renderer.printInfo("Bye!");
                    break;
                }
                if ("/tasks".equalsIgnoreCase(input)) {
                    printRecentTasks();
                    continue;
                }
                if ("/new".equalsIgnoreCase(input) || "/clear".equalsIgnoreCase(input)) {
                    currentTaskId = null;
                    renderer.printInfo("Conversation cleared. Enter your next request.");
                    continue;
                }
                if ("/resume".equalsIgnoreCase(input)) {
                    Long selectedTaskId = promptForResumeTask(terminal);
                    if (selectedTaskId != null) {
                        currentTaskId = selectedTaskId;
                        renderer.printInfo("Resumed task #" + currentTaskId + ".");
                        System.out.println();
                        System.out.println(applicationService.buildTaskContextPreview(currentTaskId));
                        renderer.printDivider();
                    }
                    continue;
                }
                if (input.toLowerCase().startsWith("/resume ")) {
                    String rawTaskId = input.substring(8).trim();
                    currentTaskId = Long.valueOf(rawTaskId);
                    renderer.printInfo("Resumed task #" + currentTaskId + ".");
                    System.out.println();
                    System.out.println(applicationService.buildTaskContextPreview(currentTaskId));
                    renderer.printDivider();
                    continue;
                }
                if ("/shell".equalsIgnoreCase(input)) {
                    String shellCommand = lineReader.readLine("shell> ");
                    if (shellCommand != null && !shellCommand.trim().isEmpty()) {
                        runLocalShell(shellCommand.trim(), cwd);
                    }
                    continue;
                }
                if (input.startsWith("!") && input.length() > 1) {
                    runLocalShell(input.substring(1).trim(), cwd);
                    continue;
                }
                if (input.toLowerCase().startsWith("/shell ")) {
                    runLocalShell(input.substring(7).trim(), cwd);
                    continue;
                }

                TaskResponse response;
                renderer.resetTurn();
                if (currentTaskId == null) {
                    response = applicationService.createTaskAndRun(new CreateTaskCommand("Interactive CLI Task", input, cwd));
                    currentTaskId = response.getTaskId();
                } else {
                    response = applicationService.continueTask(new ContinueTaskCommand(currentTaskId, input));
                }
                printTaskResponse(response);
            }
        } catch (Exception ex) {
            if (renderer != null) {
                renderer.printError("Interactive CLI failed: " + ex.getMessage());
            } else {
                System.out.println("Interactive CLI failed: " + ex.getMessage());
            }
        } finally {
            CliRenderContext.clear();
            if (terminal != null) {
                try {
                    terminal.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private Terminal createInteractiveTerminal() throws Exception {
        try {
            return TerminalBuilder.builder()
                    .system(true)
                    .jna(true)
                    .jansi(true)
                    .dumb(false)
                    .nativeSignals(true)
                    .build();
        } catch (Exception ignored) {
            return TerminalBuilder.builder()
                    .system(false)
                    .streams(System.in, System.out)
                    .jna(true)
                    .jansi(true)
                    .dumb(false)
                    .nativeSignals(true)
                    .build();
        }
    }

    private LineReader createLineReader(Terminal terminal, final AnsiCliRenderer ansiRenderer) {
        DefaultParser parser = new DefaultParser();
        parser.setEscapeChars(new char[0]);
        final LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .appName("code-agent")
                .parser(parser)
                .option(LineReader.Option.AUTO_MENU, true)
                .option(LineReader.Option.AUTO_LIST, true)
                .option(LineReader.Option.LIST_AMBIGUOUS, true)
                .option(LineReader.Option.MENU_COMPLETE, true)
                .completer(new SlashCommandCompleter())
                .build();

        reader.setAutosuggestion(LineReader.SuggestionType.COMPLETER);
        AutosuggestionWidgets autosuggestionWidgets = new AutosuggestionWidgets(reader);
        autosuggestionWidgets.enable();

        KeyMap<org.jline.reader.Binding> keyMap = reader.getKeyMaps().get(LineReader.MAIN);
        keyMap.bind(new Reference(LineReader.ACCEPT_LINE), "\r");
        keyMap.bind(new Reference(LineReader.ACCEPT_LINE), "\n");

        Widget slashGuide = new Widget() {
            @Override
            public boolean apply() {
                reader.getBuffer().write("/");
                reader.callWidget(LineReader.MENU_COMPLETE);
                lastEscPressedAt = 0L;
                return true;
            }
        };
        reader.getWidgets().put("slash-guide", slashGuide);
        keyMap.bind(new Reference("slash-guide"), "/");

        Widget acceptWithSlashPreference = new Widget() {
            @Override
            public boolean apply() {
                String buffer = reader.getBuffer().toString();
                if (buffer.startsWith("/") && !isExactSlashCommand(buffer)) {
                    reader.callWidget(LineReader.MENU_COMPLETE);
                    return true;
                }
                return reader.getBuiltinWidgets().get(LineReader.ACCEPT_LINE).apply();
            }
        };
        reader.getWidgets().put("accept-with-slash-preference", acceptWithSlashPreference);
        keyMap.bind(new Reference("accept-with-slash-preference"), KeyMap.ctrl('M'));

        Widget escapeExit = new Widget() {
            @Override
            public boolean apply() {
                if (reader.getBuffer().length() == 0) {
                    long now = System.currentTimeMillis();
                    if (now - lastEscPressedAt <= ESC_EXIT_WINDOW_MS) {
                        lastEscPressedAt = 0L;
                        reader.getBuffer().write("/exit");
                        reader.callWidget(LineReader.ACCEPT_LINE);
                    } else {
                        lastEscPressedAt = now;
                        reader.printAbove(ansiRenderer.infoText("Press ESC again within 1.5s to exit."));
                    }
                    return true;
                }
                lastEscPressedAt = 0L;
                return true;
            }
        };
        reader.getWidgets().put("escape-exit", escapeExit);
        keyMap.bind(new Reference("escape-exit"), KeyMap.esc());
        return reader;
    }

    private boolean isExactSlashCommand(String input) {
        for (String command : COMMAND_SUGGESTIONS) {
            String normalized = command.trim();
            if (normalized.equalsIgnoreCase(input.trim())) {
                return true;
            }
        }
        return false;
    }

    private Long promptForResumeTask(Terminal terminal) {
        List<TaskListItemResponse> tasks = applicationService.listRecentTasks();
        if (tasks.isEmpty()) {
            renderer.printInfo("No tasks found.");
            return null;
        }
        Attributes originalAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();
        int selectedIndex = 0;
        int renderedLines = 0;
        try {
            while (true) {
                clearResumeSelector(terminal, renderedLines);
                renderedLines = renderResumeSelector(terminal, tasks, selectedIndex);
                int key = readMenuKey(reader);
                if (key == KEY_ARROW_UP) {
                    selectedIndex = selectedIndex == 0 ? tasks.size() - 1 : selectedIndex - 1;
                    continue;
                }
                if (key == KEY_ARROW_DOWN) {
                    selectedIndex = selectedIndex == tasks.size() - 1 ? 0 : selectedIndex + 1;
                    continue;
                }
                if (key == KEY_ENTER || key == 10) {
                    clearResumeSelector(terminal, renderedLines);
                    return tasks.get(selectedIndex).getTaskId();
                }
                if (key == KEY_ESCAPE || key == NonBlockingReader.EOF) {
                    clearResumeSelector(terminal, renderedLines);
                    return null;
                }
            }
        } catch (Exception ex) {
            renderer.printError("Resume selection failed: " + ex.getMessage());
            return null;
        } finally {
            terminal.setAttributes(originalAttributes);
            terminal.flush();
        }
    }

    private int renderResumeSelector(Terminal terminal, List<TaskListItemResponse> tasks, int selectedIndex) {
        terminal.writer().println();
        terminal.writer().println("  Resume Task: use Up/Down to select, Enter to confirm, Esc to cancel");
        for (int i = 0; i < tasks.size(); i++) {
            TaskListItemResponse task = tasks.get(i);
            String prefix = i == selectedIndex ? "  > " : "    ";
            String marker = i == selectedIndex ? "\u001B[1m\u001B[36m" : "";
            String reset = i == selectedIndex ? "\u001B[0m" : "";
            String line = prefix
                    + marker
                    + "#" + task.getTaskId()
                    + " [" + task.getStatus() + "] "
                    + abbreviate(task.getTitle(), 48)
                    + reset;
            terminal.writer().println(line);
        }
        terminal.writer().flush();
        return tasks.size() + 2;
    }

    private void clearResumeSelector(Terminal terminal, int renderedLines) {
        if (renderedLines <= 0) {
            return;
        }
        for (int i = 0; i < renderedLines; i++) {
            terminal.writer().print("\u001B[1A\u001B[2K\r");
        }
        terminal.writer().flush();
    }

    private int readMenuKey(NonBlockingReader reader) throws Exception {
        int ch = reader.read();
        if (ch == KEY_WINDOWS_PREFIX_0 || ch == KEY_WINDOWS_PREFIX_224) {
            int second = reader.read(MENU_KEY_TIMEOUT_MILLIS);
            if (second == 72) {
                return KEY_ARROW_UP;
            }
            if (second == 80) {
                return KEY_ARROW_DOWN;
            }
            return second;
        }
        if (ch == KEY_ESCAPE) {
            int next = reader.read(MENU_KEY_TIMEOUT_MILLIS);
            if (next == NonBlockingReader.READ_EXPIRED) {
                return KEY_ESCAPE;
            }
            if (next == '[' || next == 'O') {
                int third = reader.read(MENU_KEY_TIMEOUT_MILLIS);
                if (third == 'A') {
                    return KEY_ARROW_UP;
                }
                if (third == 'B') {
                    return KEY_ARROW_DOWN;
                }
            }
            return KEY_ESCAPE;
        }
        return ch;
    }

    private void runLocalShell(String command, String cwd) {
        if (command == null || command.trim().isEmpty()) {
            renderer.printError("Empty local shell command.");
            return;
        }
        try {
            renderer.resetTurn();
            renderer.printInfo("当前动作: 执行本地命令");
            renderer.printToolCall("run_shell", abbreviate(command, 96));
            Map<String, String> arguments = new LinkedHashMap<String, String>();
            arguments.put("command", command);
            String argumentsJson = objectMapper.writeValueAsString(arguments);
            String confirmationMessage = toolExecutor.confirmationMessage("run_shell", argumentsJson, cwd);
            if (confirmationMessage != null && !renderer.confirm(confirmationMessage)) {
                renderer.printError("Command canceled by user.");
                renderer.printDivider();
                return;
            }
            String result = toolExecutor.execute("run_shell", argumentsJson, cwd);
            renderer.printToolResult("run_shell", result);
            renderer.printDivider();
        } catch (Exception ex) {
            renderer.printError("Local shell failed: " + ex.getMessage());
            renderer.printDivider();
        }
    }

    private void printRecentTasks() {
        List<TaskListItemResponse> tasks = applicationService.listRecentTasks();
        if (tasks.isEmpty()) {
            renderer.printInfo("No tasks found.");
            return;
        }
        System.out.println();
        for (TaskListItemResponse task : tasks) {
            System.out.println("  #" + task.getTaskId() + " [" + task.getStatus() + "] " + task.getTitle());
        }
    }

    private void printTaskResponse(TaskResponse response) {
        if ("FAILED".equalsIgnoreCase(response.getStatus())) {
            if (!renderer.hasPrintedAssistantText()) {
                System.out.println();
            }
            renderer.printError(response.getFailureReason());
        } else {
            if (!renderer.hasPrintedAssistantText()) {
                System.out.println();
                renderer.printAssistantText(response.getFinalResult());
            }
        }
        renderer.printDivider();
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace("\r", " ").replace("\n", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength - 3) + "...";
    }

    private static class SlashCommandCompleter implements Completer {

        @Override
        public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
            String buffer = line.line();
            if (!buffer.startsWith("/")) {
                return;
            }

            String lower = buffer.toLowerCase();
            for (String suggestion : COMMAND_SUGGESTIONS) {
                if (suggestion.toLowerCase().startsWith(lower)) {
                    candidates.add(candidateFor(suggestion));
                }
            }

            if (candidates.isEmpty() && "/".equals(buffer)) {
                for (String suggestion : COMMAND_SUGGESTIONS) {
                    candidates.add(candidateFor(suggestion));
                }
            }
        }

        private Candidate candidateFor(String value) {
            String description = describe(value);
            return new Candidate(value, value, "commands", description, null, null, true, 0);
        }

        private String describe(String command) {
            if ("/new".equals(command) || "/clear".equals(command)) {
                return "start a fresh conversation";
            }
            if ("/resume".equals(command)) {
                return "continue a previous task";
            }
            if ("/tasks".equals(command)) {
                return "show recent tasks";
            }
            if ("/help".equals(command)) {
                return "show command help";
            }
            if ("/shell".equals(command)) {
                return "run a local shell command";
            }
            if ("/exit".equals(command)) {
                return "exit the CLI";
            }
            return "command";
        }
    }

}
