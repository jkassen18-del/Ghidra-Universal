package io.ghidrauniversal.app;

import io.ghidrauniversal.core.project.GuProject;
import io.ghidrauniversal.core.project.Workspace;
import io.ghidrauniversal.engine.GhidraInstall;
import io.ghidrauniversal.engine.HeadlessGhidraEngine;
import io.ghidrauniversal.mcp.AnalysisContext;
import io.ghidrauniversal.mcp.GhidraTools;
import io.ghidrauniversal.mcp.McpDispatcher;
import io.ghidrauniversal.mcp.McpServer;
import io.ghidrauniversal.mcp.McpServerConfig;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Application bootstrap and headless entry points.
 *
 * <pre>
 *   (no args)           print a readiness report
 *   info                same as no args
 *   serve &lt;binary&gt; [port]  analyze a binary and run the MCP server (blocks)
 * </pre>
 *
 * The JavaFX GUI ({@code GhidraUniversalApp}) is wired in Phase 4.
 */
public final class Launcher {

    public static void main(String[] args) throws Exception {
        String command = args.length > 0 ? args[0] : "info";
        switch (command) {
            case "serve" -> serve(args);
            case "info" -> info();
            default -> {
                System.err.println("unknown command: " + command);
                System.err.println("usage: (info) | serve <binary> [port]");
                System.exit(2);
            }
        }
    }

    private static void info() {
        System.out.println("Ghidra-Universal " + version());
        Workspace workspace = Workspace.defaultWorkspace();
        System.out.println("Workspace: " + workspace.root());
        Optional<GhidraInstall> install = GhidraInstall.discover(appDir().resolve("ghidra"));
        System.out.println(install
                .map(i -> "Ghidra:    " + i.installDir() + " (ready)")
                .orElse("Ghidra:    not found. Set GHIDRA_INSTALL_DIR or use a portable bundle."));
        System.out.println();
        System.out.println("Run the MCP server:  ghidra-universal serve <binary> [port]");
        System.out.println("GUI launches here in a later phase.");
    }

    private static void serve(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: serve <binary> [port]");
            System.exit(2);
            return;
        }
        Path binary = Path.of(args[1]);
        int port = args.length > 2 ? Integer.parseInt(args[2]) : 0;

        GhidraInstall install = GhidraInstall.discover(appDir().resolve("ghidra"))
                .orElseThrow(() -> new IllegalStateException(
                        "Ghidra not found. Set GHIDRA_INSTALL_DIR or use a portable bundle."));

        Workspace workspace = Workspace.defaultWorkspace();
        HeadlessGhidraEngine engine = new HeadlessGhidraEngine(install, workspace, System.out::println);
        AnalysisContext ctx = new AnalysisContext(engine);

        System.out.println("Importing and analyzing " + binary + " (first run may take a minute)...");
        GuProject project = workspace.importBinary(binary, null);
        ctx.open(project);
        System.out.println("Analysis ready for project " + project.id());

        McpDispatcher dispatcher = new McpDispatcher(
                "ghidra-universal", version(), new GhidraTools(ctx).all());
        McpServer server = new McpServer(
                McpServerConfig.defaults().withPort(port), dispatcher, System.out::println);
        server.start();

        System.out.println();
        System.out.println("MCP server ready. Connect your AI client to this SSE URL:");
        System.out.println("    " + server.sseUrl());
        System.out.println();
        System.out.println("Claude CLI:  claude mcp add --transport sse ghidra-universal " + server.sseUrl());
        System.out.println("Press Ctrl-C to stop.");

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        Thread.currentThread().join();
    }

    private static String version() {
        String v = Launcher.class.getPackage().getImplementationVersion();
        return v != null ? v : "0.1.0-dev";
    }

    private static Path appDir() {
        String appHome = System.getProperty("app.home");
        return appHome != null ? Path.of(appHome) : Path.of(System.getProperty("user.dir"));
    }

    private Launcher() {}
}
