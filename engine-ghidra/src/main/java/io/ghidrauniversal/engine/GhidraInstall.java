package io.ghidrauniversal.engine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Locates a usable Ghidra installation and its {@code analyzeHeadless} launcher.
 *
 * <p>Resolution order:</p>
 * <ol>
 *   <li>explicit path passed to {@link #at(Path)};</li>
 *   <li>the {@code GHIDRA_INSTALL_DIR} environment variable;</li>
 *   <li>a Ghidra bundled next to the application (the portable build);</li>
 * </ol>
 */
public final class GhidraInstall {

    private final Path installDir;

    private GhidraInstall(Path installDir) {
        this.installDir = installDir;
    }

    public Path installDir() {
        return installDir;
    }

    /** The platform-appropriate {@code analyzeHeadless} launcher script. */
    public Path analyzeHeadless() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String script = windows ? "analyzeHeadless.bat" : "analyzeHeadless";
        return installDir.resolve("support").resolve(script);
    }

    public boolean isValid() {
        return Files.isRegularFile(analyzeHeadless());
    }

    /** Wrap an explicit installation directory. */
    public static GhidraInstall at(Path dir) {
        return new GhidraInstall(dir.toAbsolutePath().normalize());
    }

    /**
     * Discover an installation from the environment or a bundled copy.
     *
     * @param bundledRoot directory that may contain a bundled Ghidra (may be null)
     */
    public static Optional<GhidraInstall> discover(Path bundledRoot) {
        String env = System.getenv("GHIDRA_INSTALL_DIR");
        if (env != null && !env.isBlank()) {
            GhidraInstall fromEnv = at(Path.of(env));
            if (fromEnv.isValid()) {
                return Optional.of(fromEnv);
            }
        }
        if (bundledRoot != null) {
            Optional<GhidraInstall> bundled = findUnder(bundledRoot);
            if (bundled.isPresent()) {
                return bundled;
            }
        }
        return Optional.empty();
    }

    /** Find a {@code ghidra_*} directory directly under {@code parent}. */
    private static Optional<GhidraInstall> findUnder(Path parent) {
        if (!Files.isDirectory(parent)) {
            return Optional.empty();
        }
        // A direct hit: parent itself is a Ghidra install.
        GhidraInstall direct = at(parent);
        if (direct.isValid()) {
            return Optional.of(direct);
        }
        try (var entries = Files.list(parent)) {
            return entries
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().toLowerCase().startsWith("ghidra"))
                    .map(GhidraInstall::at)
                    .filter(GhidraInstall::isValid)
                    .findFirst();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
