package org.dean.codex.tools.local;

import org.dean.codex.protocol.tool.FileReadResult;
import org.dean.codex.protocol.tool.FilePatchResult;
import org.dean.codex.protocol.tool.FileSearchResult;
import org.dean.codex.protocol.tool.ListDirResult;
import org.dean.codex.protocol.tool.FileWriteResult;
import org.dean.codex.protocol.tool.CommandApprovalDecision;
import org.dean.codex.protocol.tool.ExecCommandResult;
import org.dean.codex.protocol.tool.ShellCommandResult;
import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.tools.local.exec.InMemoryExecSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalToolsTest {

    @TempDir
    Path workspaceRoot;

    private FileReaderToolImpl fileReaderTool;
    private FileWriterToolImpl fileWriterTool;
    private ShellCommandToolImpl shellCommandTool;
    private ExecCommandToolImpl execCommandTool;
    private FileSearchToolImpl fileSearchTool;
    private ListDirToolImpl listDirTool;
    private FilePatchToolImpl filePatchTool;

    @BeforeEach
    void setUp() {
        fileReaderTool = new FileReaderToolImpl(workspaceRoot);
        fileWriterTool = new FileWriterToolImpl(workspaceRoot);
        InMemoryExecSessionManager execSessionManager = new InMemoryExecSessionManager();
        shellCommandTool = new ShellCommandToolImpl(
                workspaceRoot,
                new PatternCommandApprovalPolicy(PatternCommandApprovalPolicy.Mode.REVIEW_SENSITIVE),
                java.time.Duration.ofSeconds(60),
                execSessionManager);
        execCommandTool = new ExecCommandToolImpl(
                workspaceRoot,
                new PatternCommandApprovalPolicy(PatternCommandApprovalPolicy.Mode.REVIEW_SENSITIVE),
                java.time.Duration.ofSeconds(60),
                execSessionManager);
        fileSearchTool = new FileSearchToolImpl(workspaceRoot);
        listDirTool = new ListDirToolImpl(workspaceRoot);
        filePatchTool = new FilePatchToolImpl(workspaceRoot);
    }

    @Test
    void readFileReturnsContentForWorkspaceFile() throws Exception {
        Files.writeString(workspaceRoot.resolve("notes.txt"), "hello world");

        FileReadResult result = fileReaderTool.readFile("notes.txt");

        assertTrue(result.success());
        assertEquals("notes.txt", result.path());
        assertEquals("hello world", result.content());
        assertFalse(result.truncated());
        assertEquals(11, result.totalCharacters());
        assertEquals("", result.error());
    }

    @Test
    void readFileRejectsPathTraversal() {
        FileReadResult result = fileReaderTool.readFile("../outside.txt");

        assertFalse(result.success());
        assertTrue(result.error().contains("project root"));
    }

    @Test
    void readFileTruncatesLargeContent() throws Exception {
        String largeContent = "a".repeat(12_500);
        Files.writeString(workspaceRoot.resolve("large.txt"), largeContent);

        FileReadResult result = fileReaderTool.readFile("large.txt");

        assertTrue(result.success());
        assertTrue(result.truncated());
        assertEquals(12_000, result.content().length());
        assertEquals(12_500, result.totalCharacters());
        assertTrue(result.error().contains("truncated"));
    }

    @Test
    void writeFileCreatesParentsAndOverwritesExistingContent() throws Exception {
        FileWriteResult created = fileWriterTool.writeFile("nested/demo.txt", "first");
        FileWriteResult updated = fileWriterTool.writeFile("nested/demo.txt", "second");

        assertTrue(created.success());
        assertTrue(created.created());
        assertEquals(5, created.charactersWritten());
        assertTrue(updated.success());
        assertFalse(updated.created());
        assertEquals("second", Files.readString(workspaceRoot.resolve("nested/demo.txt")));
    }

    @Test
    void writeFileRejectsPathTraversal() {
        FileWriteResult result = fileWriterTool.writeFile("../../escape.txt", "nope");

        assertFalse(result.success());
        assertTrue(result.error().contains("project root"));
    }

    @Test
    void runCommandCapturesStdoutAndWorkingDirectory() {
        ShellCommandResult result = shellCommandTool.runCommand("printf 'hi from shell'");

        assertTrue(result.success());
        assertEquals(0, result.exitCode());
        assertEquals("hi from shell", result.stdout());
        assertEquals("", result.stderr());
        assertFalse(result.timedOut());
        assertEquals(workspaceRoot.toString(), result.workingDirectory());
        assertTrue(result.executed());
        assertEquals(CommandApprovalDecision.ALLOW, result.approvalDecision());
    }

    @Test
    void runCommandReportsNonZeroExitCode() {
        ShellCommandResult result = shellCommandTool.runCommand("echo boom >&2; exit 7");

        assertFalse(result.success());
        assertEquals(7, result.exitCode());
        assertTrue(result.stderr().contains("boom"));
        assertFalse(result.timedOut());
        assertTrue(result.error().contains("non-zero"));
        assertTrue(result.executed());
    }

    @Test
    void runCommandRejectsBlankCommand() {
        ShellCommandResult result = shellCommandTool.runCommand("   ");

        assertFalse(result.success());
        assertEquals(-1, result.exitCode());
        assertTrue(result.error().contains("must not be blank"));
        assertFalse(result.executed());
        assertEquals(CommandApprovalDecision.BLOCK, result.approvalDecision());
    }

    @Test
    void runCommandRequiresApprovalForSensitiveMutation() {
        ShellCommandResult result = shellCommandTool.runCommand("git commit -m 'ship it'");

        assertFalse(result.success());
        assertEquals(-1, result.exitCode());
        assertFalse(result.executed());
        assertEquals(CommandApprovalDecision.REQUIRE_APPROVAL, result.approvalDecision());
        assertTrue(result.error().contains("requires approval"));
    }

    @Test
    void execCommandStartsSessionAndWriteStdinPollsToCompletion() {
        ExecCommandResult started = execCommandTool.execCommand(
                new ThreadId("thread-1"),
                "printf 'one\\n'; sleep 1; printf 'two\\n'",
                50L,
                5_000L,
                false);

        assertTrue(started.success());
        assertTrue(started.executed());
        assertEquals("RUNNING", started.status());
        assertTrue(started.stdout().contains("one"));
        assertFalse(started.sessionId().isBlank());

        ExecCommandResult polled = execCommandTool.writeStdin(
                new ThreadId("thread-1"),
                started.sessionId(),
                "",
                1_500L);
        assertTrue(polled.stdout().contains("two"));

        ExecCommandResult completed = execCommandTool.writeStdin(
                new ThreadId("thread-1"),
                started.sessionId(),
                "",
                500L);
        assertEquals("COMPLETED", completed.status());
        assertEquals(CommandApprovalDecision.ALLOW, completed.approvalDecision());
    }

    @Test
    void execCommandRequiresApprovalForSensitiveMutation() {
        ExecCommandResult result = execCommandTool.execCommand(
                new ThreadId("thread-1"),
                "git commit -m 'ship it'",
                null,
                null,
                false);

        assertFalse(result.success());
        assertFalse(result.executed());
        assertEquals("APPROVAL_REQUIRED", result.status());
        assertEquals(CommandApprovalDecision.REQUIRE_APPROVAL, result.approvalDecision());
        assertTrue(result.error().contains("requires approval"));
    }

    @Test
    void runCommandTimesOutThroughExecSessionManager() {
        ShellCommandToolImpl shortTimeoutTool = new ShellCommandToolImpl(
                workspaceRoot,
                new PatternCommandApprovalPolicy(PatternCommandApprovalPolicy.Mode.REVIEW_SENSITIVE),
                java.time.Duration.ofMillis(150));

        ShellCommandResult result = shortTimeoutTool.runCommand("sleep 5");

        assertFalse(result.success());
        assertTrue(result.timedOut());
        assertTrue(result.error().contains("timed out"));
    }

    @Test
    void searchFindsMatchingLinesInsideWorkspace() throws Exception {
        Files.createDirectories(workspaceRoot.resolve("src"));
        Files.writeString(workspaceRoot.resolve("src/demo.txt"), "alpha\nbeta\ngamma beta");

        FileSearchResult result = fileSearchTool.search("beta", "");

        assertTrue(result.success());
        assertEquals("beta", result.query());
        assertEquals(2, result.totalMatches());
        assertEquals(2, result.matches().size());
        assertEquals("src/demo.txt", result.matches().get(0).path());
        assertEquals(2, result.matches().get(0).lineNumber());
    }

    @Test
    void searchRejectsScopeOutsideWorkspace() {
        FileSearchResult result = fileSearchTool.search("beta", "../outside");

        assertFalse(result.success());
        assertTrue(result.error().contains("workspace root"));
    }

    @Test
    void listDirListsBoundedEntriesAndHonorsDepth() throws Exception {
        Files.createDirectories(workspaceRoot.resolve("src/main/java"));
        Files.writeString(workspaceRoot.resolve("README.md"), "hello");
        Files.writeString(workspaceRoot.resolve("src/main/java/App.java"), "class App {}");

        ListDirResult shallow = listDirTool.listDir("", 1);
        assertTrue(shallow.success());
        assertEquals(".", shallow.path());
        assertEquals(1, shallow.maxDepth());
        assertTrue(shallow.entries().stream().anyMatch(entry -> entry.path().equals("README.md")));
        assertTrue(shallow.entries().stream().anyMatch(entry -> entry.path().equals("src")));
        assertFalse(shallow.entries().stream().anyMatch(entry -> entry.path().equals("src/main/java/App.java")));

        ListDirResult deeper = listDirTool.listDir("src", 3);
        assertTrue(deeper.success());
        assertEquals("src", deeper.path());
        assertTrue(deeper.entries().stream().anyMatch(entry -> entry.path().equals("src/main/java/App.java")));
    }

    @Test
    void listDirRejectsOutsideWorkspace() {
        ListDirResult result = listDirTool.listDir("../outside", 1);

        assertFalse(result.success());
        assertTrue(result.error().contains("workspace root"));
    }

    @Test
    void listDirTruncatesLargeDirectories() throws Exception {
        Files.createDirectories(workspaceRoot.resolve("many"));
        for (int index = 0; index < 205; index++) {
            Files.writeString(workspaceRoot.resolve("many/file-%03d.txt".formatted(index)), "x");
        }

        ListDirResult result = listDirTool.listDir("many", 1);

        assertTrue(result.success());
        assertTrue(result.truncated());
        assertEquals(205, result.totalEntries());
        assertEquals(200, result.entries().size());
    }

    @Test
    void patchAppliesSingleExactReplacement() throws Exception {
        Files.createDirectories(workspaceRoot.resolve("src"));
        Files.writeString(workspaceRoot.resolve("src/App.java"), "class App { String name = \"old\"; }");

        FilePatchResult result = filePatchTool.applyPatch("src/App.java", "\"old\"", "\"new\"", false);

        assertTrue(result.success());
        assertEquals(1, result.replacements());
        assertEquals("class App { String name = \"new\"; }", Files.readString(workspaceRoot.resolve("src/App.java")));
    }

    @Test
    void patchRejectsAmbiguousSingleReplace() throws Exception {
        Files.createDirectories(workspaceRoot.resolve("src"));
        Files.writeString(workspaceRoot.resolve("src/App.java"), "old old");

        FilePatchResult result = filePatchTool.applyPatch("src/App.java", "old", "new", false);

        assertFalse(result.success());
        assertTrue(result.error().contains("multiple locations"));
    }

}
