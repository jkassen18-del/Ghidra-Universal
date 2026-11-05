package io.ghidrauniversal.mcp;

import io.ghidrauniversal.core.model.AnalysisResult;
import io.ghidrauniversal.engine.GhidraEngine;
import io.ghidrauniversal.core.project.GuProject;

/**
 * The project the MCP tools currently operate on, plus its cached analysis.
 *
 * <p>The GUI (or a CLI entry point) sets the active project; the MCP tools read
 * from {@link #analysis()} and mutate through {@link #engine()}, reloading the
 * cache after a write. One context is shared by all tool handlers, so access is
 * synchronized.</p>
 */
public final class AnalysisContext {

    private final GhidraEngine engine;
    private GuProject project;
    private AnalysisResult analysis;

    public AnalysisContext(GhidraEngine engine) {
        this.engine = engine;
    }

    public GhidraEngine engine() {
        return engine;
    }

    /** Load (analyzing if needed) and make a project the active one. */
    public synchronized void open(GuProject p) throws GhidraEngine.EngineException {
        this.analysis = engine.load(p);
        this.project = p;
    }

    public synchronized boolean isOpen() {
        return project != null && analysis != null;
    }

    public synchronized GuProject project() {
        requireOpen();
        return project;
    }

    public synchronized AnalysisResult analysis() {
        requireOpen();
        return analysis;
    }

    /** Re-read the cached analysis after a write-back mutation. */
    public synchronized void reload() throws GhidraEngine.EngineException {
        requireOpen();
        this.analysis = engine.load(project);
    }

    private void requireOpen() {
        if (project == null || analysis == null) {
            throw new IllegalStateException("no project is open");
        }
    }
}
