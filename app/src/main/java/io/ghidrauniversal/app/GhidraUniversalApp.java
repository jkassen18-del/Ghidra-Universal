package io.ghidrauniversal.app;

import io.ghidrauniversal.core.Json;
import io.ghidrauniversal.core.model.AnalysisResult;
import io.ghidrauniversal.core.model.Disassembly;
import io.ghidrauniversal.core.model.FunctionInfo;
import io.ghidrauniversal.core.model.Instruction;
import io.ghidrauniversal.core.project.GuProject;
import io.ghidrauniversal.core.project.Workspace;
import io.ghidrauniversal.engine.GhidraInstall;
import io.ghidrauniversal.engine.HeadlessGhidraEngine;
import io.ghidrauniversal.mcp.AnalysisContext;
import io.ghidrauniversal.mcp.GhidraTools;
import io.ghidrauniversal.mcp.McpDispatcher;
import io.ghidrauniversal.mcp.McpServer;
import io.ghidrauniversal.mcp.McpServerConfig;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The Ghidra-Universal desktop GUI.
 *
 * <p>Open a binary; it is imported and analyzed by Ghidra on a background
 * thread, the function list and views populate, and the local MCP server starts
 * so an AI client can attach. Functions can be renamed/commented from the list
 * (write-back through the engine).</p>
 */
public final class GhidraUniversalApp extends Application {

    private final Workspace workspace = Workspace.defaultWorkspace();
    private HeadlessGhidraEngine engine;
    private AnalysisContext ctx;
    private McpServer server;
    private GuProject project;
    private AnalysisResult analysis;

    private final ObservableList<FunctionInfo> functions = FXCollections.observableArrayList();
    private FilteredList<FunctionInfo> filtered;

    private ListView<FunctionInfo> functionList;
    private TextArea decompiledArea;
    private TextArea disassemblyArea;
    private TextArea infoArea;
    private TextField filterField;
    private TextField mcpUrlField;
    private Label statusLabel;
    private ProgressBar progress;
    private Button openButton;

    @Override
    public void start(Stage stage) {
        Optional<GhidraInstall> install = GhidraInstall.discover(appDir().resolve("ghidra"));
        install.ifPresent(i -> {
            engine = new HeadlessGhidraEngine(i, workspace, this::logLater);
            ctx = new AnalysisContext(engine);
        });

        stage.setTitle("Ghidra-Universal");
        stage.setScene(new Scene(buildUi(install.isPresent()), 1100, 720));
        stage.show();

        maybeAutoOpen();
        maybeScreenshotAndExit(stage);
    }

    private Region buildUi(boolean ghidraReady) {
        BorderPane root = new BorderPane();
        root.setTop(buildToolbar());
        root.setLeft(buildFunctionPane());
        root.setCenter(buildCenter());
        root.setBottom(buildStatusBar());
        if (!ghidraReady) {
            setStatus("Ghidra not found — set GHIDRA_INSTALL_DIR or use a portable bundle.");
            openButton.setDisable(true);
        } else {
            setStatus("Ready. Open a binary to analyze.");
        }
        return root;
    }

    private ToolBar buildToolbar() {
        openButton = new Button("Open binary…");
        openButton.setOnAction(e -> chooseBinary());

        mcpUrlField = new TextField();
        mcpUrlField.setEditable(false);
        mcpUrlField.setPrefWidth(320);
        mcpUrlField.setPromptText("MCP server URL appears here once a binary is open");
        Button copy = new Button("Copy URL");
        copy.setOnAction(e -> {
            if (!mcpUrlField.getText().isBlank()) {
                var cc = new javafx.scene.input.ClipboardContent();
                cc.putString(mcpUrlField.getText());
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
                setStatus("MCP URL copied to clipboard.");
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        progress = new ProgressBar(0);
        progress.setVisible(false);
        progress.setPrefWidth(160);

        return new ToolBar(openButton, new Label("  MCP:"), mcpUrlField, copy, spacer, progress);
    }

    private Region buildFunctionPane() {
        filterField = new TextField();
        filterField.setPromptText("Filter functions…");
        filtered = new FilteredList<>(functions, f -> true);
        filterField.textProperty().addListener((obs, old, text) -> {
            String q = text == null ? "" : text.toLowerCase();
            filtered.setPredicate(f -> q.isEmpty() || f.name().toLowerCase().contains(q));
        });

        functionList = new ListView<>(filtered);
        functionList.setPrefWidth(300);
        functionList.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(FunctionInfo f, boolean empty) {
                super.updateItem(f, empty);
                setText(empty || f == null ? null : f.name() + "   " + f.address());
            }
        });
        functionList.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, f) -> showFunction(f));
        functionList.setContextMenu(buildFunctionMenu());
        VBox.setVgrow(functionList, Priority.ALWAYS);

        VBox box = new VBox(6, new Label("Functions"), filterField, functionList);
        box.setPadding(new Insets(8));
        return box;
    }

    private ContextMenu buildFunctionMenu() {
        MenuItem rename = new MenuItem("Rename…");
        rename.setOnAction(e -> renameSelected());
        MenuItem comment = new MenuItem("Add comment…");
        comment.setOnAction(e -> commentSelected());
        return new ContextMenu(rename, comment);
    }

    private Region buildCenter() {
        decompiledArea = monospaceArea("Select a function to view its decompiled C.");
        disassemblyArea = monospaceArea("Select a function to view its disassembly.");
        infoArea = monospaceArea("Program information appears here after analysis.");

        TabPane tabs = new TabPane(
                tab("Decompiled C", decompiledArea),
                tab("Disassembly", disassemblyArea),
                tab("Program info", infoArea));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        return tabs;
    }

    private Region buildStatusBar() {
        statusLabel = new Label();
        HBox bar = new HBox(statusLabel);
        bar.setPadding(new Insets(4, 8, 4, 8));
        return bar;
    }

    // --- actions ---

    private void chooseBinary() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open a binary to analyze");
        Path file = Optional.ofNullable(chooser.showOpenDialog(openButton.getScene().getWindow()))
                .map(java.io.File::toPath).orElse(null);
        if (file != null) {
            openBinary(file);
        }
    }

    private void openBinary(Path binary) {
        if (engine == null) {
            setStatus("Ghidra is not available.");
            return;
        }
        setBusy(true, "Analyzing " + binary.getFileName() + " … (first run may take a minute)");
        Task<AnalysisResult> task = new Task<>() {
            @Override protected AnalysisResult call() throws Exception {
                GuProject p = workspace.importBinary(binary, null);
                ctx.open(p);
                project = p;
                return ctx.analysis();
            }
        };
        task.setOnSucceeded(e -> {
            analysis = task.getValue();
            populate(analysis);
            startServer();
            setBusy(false, "Analyzed " + analysis.functions().size() + " functions. MCP server running.");
        });
        task.setOnFailed(e -> {
            setBusy(false, "Analysis failed: " + describe(task.getException()));
        });
        runDaemon(task);
    }

    private void populate(AnalysisResult r) {
        functions.setAll(r.functions());
        infoArea.setText(Json.write(r.program()));
        if (!functions.isEmpty()) {
            functionList.getSelectionModel().select(0);
        }
    }

    private void startServer() {
        try {
            if (server != null) {
                server.stop();
            }
            McpDispatcher dispatcher = new McpDispatcher(
                    "ghidra-universal", appVersion(), new GhidraTools(ctx).all());
            server = new McpServer(McpServerConfig.defaults(), dispatcher, this::logLater);
            server.start();
            mcpUrlField.setText(server.sseUrl());
        } catch (Exception e) {
            setStatus("Could not start MCP server: " + describe(e));
        }
    }

    private void showFunction(FunctionInfo f) {
        if (f == null || analysis == null) {
            return;
        }
        decompiledArea.setText(analysis.decompiled().stream()
                .filter(d -> d.address().equalsIgnoreCase(f.address()))
                .map(d -> d.code()).findFirst()
                .orElse("// no decompilation available for " + f.name()));
        disassemblyArea.setText(formatDisassembly(f));
    }

    private String formatDisassembly(FunctionInfo f) {
        Optional<Disassembly> d = analysis.disassembly().stream()
                .filter(x -> x.address().equalsIgnoreCase(f.address())).findFirst();
        if (d.isEmpty()) {
            return "; no disassembly for " + f.name();
        }
        StringBuilder sb = new StringBuilder();
        for (Instruction ins : d.get().instructions()) {
            sb.append(ins.address()).append("  ")
              .append(ins.mnemonic());
            if (!ins.operands().isBlank()) {
                sb.append(' ').append(ins.operands());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private void renameSelected() {
        FunctionInfo f = functionList.getSelectionModel().getSelectedItem();
        if (f == null) {
            return;
        }
        TextInputDialog dialog = new TextInputDialog(f.name());
        dialog.setTitle("Rename function");
        dialog.setHeaderText("Rename " + f.name() + " (" + f.address() + ")");
        dialog.setContentText("New name:");
        dialog.showAndWait().ifPresent(newName -> applyWrite(
                "rename", () -> engine.renameFunction(project, f.address(), newName)));
    }

    private void commentSelected() {
        FunctionInfo f = functionList.getSelectionModel().getSelectedItem();
        if (f == null) {
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add comment");
        dialog.setHeaderText("Comment at " + f.name() + " (" + f.address() + ")");
        dialog.setContentText("Comment:");
        dialog.showAndWait().ifPresent(text -> applyWrite(
                "comment", () -> engine.setComment(project, f.address(), text)));
    }

    private void applyWrite(String label, ThrowingRunnable action) {
        setBusy(true, "Applying " + label + " …");
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                action.run();
                ctx.reload();
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            analysis = ctx.analysis();
            String selectedAddr = Optional.ofNullable(functionList.getSelectionModel().getSelectedItem())
                    .map(FunctionInfo::address).orElse(null);
            populate(analysis);
            if (selectedAddr != null) {
                functions.stream().filter(fi -> fi.address().equalsIgnoreCase(selectedAddr))
                        .findFirst().ifPresent(fi -> functionList.getSelectionModel().select(fi));
            }
            setBusy(false, label + " applied.");
        });
        task.setOnFailed(e -> setBusy(false, label + " failed: " + describe(task.getException())));
        runDaemon(task);
    }

    // --- helpers ---

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static TextArea monospaceArea(String prompt) {
        TextArea area = new TextArea();
        area.setEditable(false);
        area.setPromptText(prompt);
        area.setStyle("-fx-font-family: 'monospace';");
        return area;
    }

    private static Tab tab(String title, Region content) {
        return new Tab(title, content);
    }

    private void setBusy(boolean busy, String status) {
        progress.setVisible(busy);
        progress.setProgress(busy ? ProgressBar.INDETERMINATE_PROGRESS : 0);
        openButton.setDisable(busy);
        setStatus(status);
    }

    private void setStatus(String s) {
        if (statusLabel != null) {
            statusLabel.setText(s);
        }
    }

    private void logLater(String line) {
        // Engine/server logs; surface the latest line in the status bar.
        Platform.runLater(() -> setStatus(line));
    }

    private static void runDaemon(Task<?> task) {
        Thread t = new Thread(task, "gu-task");
        t.setDaemon(true);
        t.start();
    }

    private static String describe(Throwable t) {
        return t == null ? "unknown" : (t.getMessage() != null ? t.getMessage() : t.toString());
    }

    private String appVersion() {
        String v = getClass().getPackage().getImplementationVersion();
        return v != null ? v : "0.1.0-dev";
    }

    private static Path appDir() {
        String appHome = System.getProperty("app.home");
        return appHome != null ? Path.of(appHome) : Path.of(System.getProperty("user.dir"));
    }

    // --- headless validation hooks (used under Xvfb) ---

    private void maybeAutoOpen() {
        String auto = System.getProperty("gu.autoOpen");
        if (auto != null && !auto.isBlank()) {
            openBinary(Path.of(auto));
        }
    }

    private void maybeScreenshotAndExit(Stage stage) {
        String shot = System.getProperty("gu.screenshot");
        long exitAfter = Long.getLong("gu.exitAfterMs", 0L);
        if (shot == null && exitAfter == 0L) {
            return;
        }
        long delay = exitAfter > 0 ? exitAfter : 1500L;
        var pause = new javafx.animation.PauseTransition(javafx.util.Duration.millis(delay));
        pause.setOnFinished(e -> {
            if (shot != null) {
                Screenshots.save(stage.getScene(), Path.of(shot));
            }
            Platform.exit();
        });
        pause.play();
    }

    public static void launchGui(String[] args) {
        Application.launch(GhidraUniversalApp.class, args);
    }
}
