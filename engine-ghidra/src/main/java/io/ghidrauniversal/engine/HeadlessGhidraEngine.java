package io.ghidrauniversal.engine;

import io.ghidrauniversal.core.Json;
import io.ghidrauniversal.core.model.AnalysisResult;
import io.ghidrauniversal.core.project.GuProject;
import io.ghidrauniversal.core.project.Workspace;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * {@link GhidraEngine} backed by Ghidra's {@code analyzeHeadless} launcher.
 *
 * <p>One persistent Ghidra project ({@value #PROJECT_NAME}) lives in each
 * {@link GuProject}'s {@code ghidra/} directory. The binary is imported and
 * analyzed once; reads re-run the export script against the saved project and
 * writes re-run it after mutating.</p>
 */
public final class HeadlessGhidraEngine implements GhidraEngine {

    private static final String PROJECT_NAME = "gu";
    private static final String EXPORT_SCRIPT = "ExportProgram.java";
    private static final String WRITE_SCRIPT = "WriteAnnotation.java";

    private static final long ANALYZE_TIMEOUT_SECS = 1800;
    private static final long PROCESS_TIMEOUT_SECS = 600;

    private final GhidraInstall install;
    private final Workspace workspace;
    private final Consumer<String> log;
    private volatile Path scriptDir;

    public HeadlessGhidraEngine(GhidraInstall install, Workspace workspace) {
        this(install, workspace, line -> {});
    }

    public HeadlessGhidraEngine(GhidraInstall install, Workspace workspace, Consumer<String> log) {
        this.install = install;
        this.workspace = workspace;
        this.log = log != null ? log : line -> {};
    }

    @Override
    public AnalysisResult analyze(GuProject project) throws EngineException {
        Path binary = workspace.binaryFile(project);
        if (!Files.isRegularFile(binary)) {
            throw new EngineException("binary not found: " + binary);
        }
        Path projectLoc = project.ghidraDir(workspace.root());
        Path outJson = project.analysisJson(workspace.root());

        List<String> args = new ArrayList<>(List.of(
                projectLoc.toString(), PROJECT_NAME,
                "-import", binary.toString(),
                "-overwrite",
                "-scriptPath", scriptDir().toString(),
                "-postScript", EXPORT_SCRIPT, outJson.toString()));
        runHeadless(args, ANALYZE_TIMEOUT_SECS, "analyze");
        return parse(outJson);
    }

    @Override
    public AnalysisResult load(GuProject project) throws EngineException {
        Path outJson = project.analysisJson(workspace.root());
        if (Files.isRegularFile(outJson)) {
            return parse(outJson);
        }
        return analyze(project);
    }

    @Override
    public void renameFunction(GuProject project, String address, String newName)
            throws EngineException {
        write(project, List.of("rename", address, newName));
    }

    @Override
    public void setComment(GuProject project, String address, String comment)
            throws EngineException {
        write(project, List.of("comment", address, comment));
    }

    @Override
    public void retypeVariable(GuProject project, String functionAddress, String variable,
                               String type) throws EngineException {
        write(project, List.of("retype", functionAddress, variable, type));
    }

    // --- internals ---

    private void write(GuProject project, List<String> scriptArgs) throws EngineException {
        Path projectLoc = project.ghidraDir(workspace.root());
        Path outJson = project.analysisJson(workspace.root());

        List<String> args = new ArrayList<>(List.of(
                projectLoc.toString(), PROJECT_NAME,
                "-process", project.binaryFileName(),
                "-noanalysis",
                "-scriptPath", scriptDir().toString(),
                "-postScript", WRITE_SCRIPT));
        args.addAll(scriptArgs);
        runHeadless(args, PROCESS_TIMEOUT_SECS, "write:" + scriptArgs.get(0));

        // Refresh the cached analysis so reads reflect the annotation.
        List<String> refresh = new ArrayList<>(List.of(
                projectLoc.toString(), PROJECT_NAME,
                "-process", project.binaryFileName(),
                "-noanalysis",
                "-scriptPath", scriptDir().toString(),
                "-postScript", EXPORT_SCRIPT, outJson.toString()));
        runHeadless(refresh, PROCESS_TIMEOUT_SECS, "re-export");
    }

    private void runHeadless(List<String> args, long timeout, String label) throws EngineException {
        if (!install.isValid()) {
            throw new EngineException("Ghidra not found at " + install.installDir());
        }
        List<String> cmd = ProcessRunner.launcher(install.analyzeHeadless(), args);
        log.accept("[" + label + "] " + String.join(" ", cmd));
        try {
            ProcessRunner.Result result = ProcessRunner.run(
                    cmd, install.installDir(), timeout, log, ghidraEnv());
            if (result.timedOut()) {
                throw new EngineException("Ghidra " + label + " timed out after " + timeout + "s");
            }
            if (!result.success()) {
                throw new EngineException("Ghidra " + label + " exited " + result.exitCode()
                        + "\n" + tail(result.output(), 40));
            }
        } catch (IOException e) {
            throw new EngineException("failed to run Ghidra " + label, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EngineException("interrupted during Ghidra " + label, e);
        }
    }

    /**
     * Environment for the Ghidra subprocess. Ghidra's {@code analyzeHeadless}
     * finds its JVM via {@code JAVA_HOME}; we point it at the JVM this app is
     * running on — which, in a packaged build, is the bundled runtime — so the
     * tool works on a machine with no system Java installed. (Honors an
     * explicit {@code GU_GHIDRA_JAVA_HOME} override if set.)
     */
    private Map<String, String> ghidraEnv() {
        String javaHome = System.getenv().getOrDefault(
                "GU_GHIDRA_JAVA_HOME", System.getProperty("java.home"));
        if (javaHome == null || javaHome.isBlank()) {
            return Map.of();
        }
        Map<String, String> env = new HashMap<>();
        env.put("JAVA_HOME", javaHome);
        String binDir = Path.of(javaHome, "bin").toString();
        String path = System.getenv("PATH");
        env.put("PATH", path == null || path.isBlank()
                ? binDir : binDir + File.pathSeparator + path);
        return env;
    }

    private AnalysisResult parse(Path outJson) throws EngineException {
        if (!Files.isRegularFile(outJson)) {
            throw new EngineException("export produced no output: " + outJson);
        }
        try {
            return Json.read(outJson, AnalysisResult.class);
        } catch (UncheckedIOException e) {
            throw new EngineException("failed to parse analysis JSON: " + outJson, e.getCause());
        }
    }

    /** Lazily materialize the bundled Ghidra scripts onto disk for -scriptPath. */
    private Path scriptDir() {
        Path dir = scriptDir;
        if (dir != null) {
            return dir;
        }
        synchronized (this) {
            if (scriptDir == null) {
                Path target = workspace.root().resolve("scripts");
                try {
                    Files.createDirectories(target);
                    copyResource(EXPORT_SCRIPT, target.resolve(EXPORT_SCRIPT));
                    copyResource(WRITE_SCRIPT, target.resolve(WRITE_SCRIPT));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                scriptDir = target;
            }
            return scriptDir;
        }
    }

    private void copyResource(String name, Path target) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/ghidra_scripts/" + name)) {
            if (in == null) {
                throw new IOException("bundled script not found on classpath: " + name);
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String tail(String s, int lines) {
        String[] arr = s.split("\n");
        int from = Math.max(0, arr.length - lines);
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < arr.length; i++) {
            sb.append(arr[i]).append('\n');
        }
        return sb.toString();
    }
}
