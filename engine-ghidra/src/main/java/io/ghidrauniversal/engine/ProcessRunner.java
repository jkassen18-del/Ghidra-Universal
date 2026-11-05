package io.ghidrauniversal.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Runs an external command, capturing combined output, with a timeout. */
final class ProcessRunner {

    record Result(int exitCode, String output, boolean timedOut) {
        boolean success() {
            return !timedOut && exitCode == 0;
        }
    }

    /**
     * Run {@code command} in {@code workingDir}. stdout and stderr are merged
     * and streamed to {@code lineSink} (if non-null) as well as captured.
     */
    static Result run(List<String> command, Path workingDir, long timeoutSeconds,
                      Consumer<String> lineSink) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDir != null) {
            pb.directory(workingDir.toFile());
        }
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder captured = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    captured.append(line).append('\n');
                    if (lineSink != null) {
                        lineSink.accept(line);
                    }
                }
            } catch (IOException ignored) {
                // process ended / stream closed
            }
        }, "ghidra-proc-reader");
        reader.setDaemon(true);
        reader.start();

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            reader.join(2000);
            return new Result(-1, captured.toString(), true);
        }
        reader.join(5000);
        return new Result(process.exitValue(), captured.toString(), false);
    }

    /** Wrap a launcher invocation so a Windows {@code .bat} runs via cmd.exe. */
    static List<String> launcher(Path launcher, List<String> args) {
        List<String> cmd = new ArrayList<>();
        if (launcher.toString().toLowerCase().endsWith(".bat")) {
            cmd.add("cmd.exe");
            cmd.add("/c");
        }
        cmd.add(launcher.toString());
        cmd.addAll(args);
        return cmd;
    }

    private ProcessRunner() {}
}
