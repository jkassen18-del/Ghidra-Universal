package io.ghidrauniversal.app;

import io.ghidrauniversal.engine.GhidraInstall;
import io.ghidrauniversal.core.project.Workspace;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Application bootstrap.
 *
 * <p>Phase 1: resolves the workspace and locates Ghidra, printing a readiness
 * report. The JavaFX GUI ({@code GhidraUniversalApp}) and the MCP server launch
 * are wired in here in later phases.</p>
 */
public final class Launcher {

    public static void main(String[] args) {
        System.out.println("Ghidra-Universal " + version());

        Workspace workspace = Workspace.defaultWorkspace();
        System.out.println("Workspace: " + workspace.root());

        Path bundledRoot = appDir().resolve("ghidra");
        Optional<GhidraInstall> install = GhidraInstall.discover(bundledRoot);
        if (install.isPresent()) {
            System.out.println("Ghidra:    " + install.get().installDir() + " (ready)");
        } else {
            System.out.println("Ghidra:    not found.");
            System.out.println("           Set GHIDRA_INSTALL_DIR or use a portable bundle "
                    + "with Ghidra under " + bundledRoot + ".");
        }

        System.out.println();
        System.out.println("GUI and MCP server start here in later phases.");
    }

    private static String version() {
        String v = Launcher.class.getPackage().getImplementationVersion();
        return v != null ? v : "0.1.0-dev";
    }

    /** Directory the application is installed in (best effort for dev runs). */
    private static Path appDir() {
        String appHome = System.getProperty("app.home");
        if (appHome != null) {
            return Path.of(appHome);
        }
        return Path.of(System.getProperty("user.dir"));
    }

    private Launcher() {}
}
