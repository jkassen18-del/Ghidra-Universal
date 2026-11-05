package io.ghidrauniversal.core.project;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.nio.file.Path;

/**
 * Metadata for one analyzed binary's persistent workspace.
 *
 * <p>Each project owns a directory under the workspace root:</p>
 * <pre>
 *   &lt;root&gt;/projects/&lt;id&gt;/
 *       binary/&lt;original-file&gt;     the uploaded input, untouched
 *       ghidra/                      the persistent Ghidra project (.gpr + .rep)
 *       analysis.json                cached {@link io.ghidrauniversal.core.model.AnalysisResult}
 *       project.json                 this metadata
 * </pre>
 *
 * @param id             stable workspace id (also the directory name)
 * @param displayName    human-facing name (defaults to the binary file name)
 * @param binaryFileName original file name of the analyzed binary
 * @param sha256         SHA-256 of the binary, for identity/dedup
 * @param createdEpochMs creation time, epoch millis
 * @param status         current lifecycle status
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GuProject(
        String id,
        String displayName,
        String binaryFileName,
        String sha256,
        long createdEpochMs,
        Status status) {

    public enum Status {
        /** Binary imported, not yet analyzed. */
        IMPORTED,
        /** Ghidra auto-analysis in progress. */
        ANALYZING,
        /** Analysis complete, {@code analysis.json} available. */
        READY,
        /** Analysis failed; see logs. */
        FAILED
    }

    public GuProject withStatus(Status newStatus) {
        return new GuProject(id, displayName, binaryFileName, sha256, createdEpochMs, newStatus);
    }

    // --- Convenience paths, resolved against the project directory ---

    public Path dir(Path workspaceRoot) {
        return workspaceRoot.resolve("projects").resolve(id);
    }

    public Path binaryDir(Path workspaceRoot) {
        return dir(workspaceRoot).resolve("binary");
    }

    public Path ghidraDir(Path workspaceRoot) {
        return dir(workspaceRoot).resolve("ghidra");
    }

    public Path analysisJson(Path workspaceRoot) {
        return dir(workspaceRoot).resolve("analysis.json");
    }

    public Path metadataJson(Path workspaceRoot) {
        return dir(workspaceRoot).resolve("project.json");
    }
}
