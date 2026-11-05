package io.ghidrauniversal.engine;

import io.ghidrauniversal.core.model.AnalysisResult;
import io.ghidrauniversal.core.project.GuProject;

/**
 * The operations the rest of the app performs against a Ghidra project.
 *
 * <p>Read operations are served from the cached {@link AnalysisResult} where
 * possible; mutating operations re-open the persistent Ghidra project through a
 * headless write script and then refresh the cache.</p>
 */
public interface GhidraEngine {

    /**
     * Run (or re-run) auto-analysis on the project's binary, producing and
     * caching the full {@link AnalysisResult}. Long-running; callers should run
     * it off the UI thread and observe {@link GuProject.Status}.
     */
    AnalysisResult analyze(GuProject project) throws EngineException;

    /** Load the cached analysis for a project, running analysis if absent. */
    AnalysisResult load(GuProject project) throws EngineException;

    // --- Write-back (annotate) operations ---

    void renameFunction(GuProject project, String address, String newName) throws EngineException;

    void setComment(GuProject project, String address, String comment) throws EngineException;

    void retypeVariable(GuProject project, String functionAddress, String variable, String type)
            throws EngineException;

    /** Thrown when a Ghidra invocation fails or produces unusable output. */
    class EngineException extends Exception {
        public EngineException(String message) {
            super(message);
        }

        public EngineException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
