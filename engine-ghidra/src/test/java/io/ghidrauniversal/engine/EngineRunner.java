package io.ghidrauniversal.engine;

import io.ghidrauniversal.core.model.AnalysisResult;
import io.ghidrauniversal.core.model.FunctionInfo;
import io.ghidrauniversal.core.project.GuProject;
import io.ghidrauniversal.core.project.Workspace;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Manual end-to-end validation runner for the Ghidra engine (not a JUnit test
 * — it drives real Ghidra). Run via the {@code engineSmoke} Gradle task:
 *
 * <pre>GHIDRA_INSTALL_DIR=... ./gradlew :engine-ghidra:engineSmoke -Pbinary=/path/to/sample</pre>
 */
public final class EngineRunner {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: EngineRunner <binary> <workspaceRoot>");
            System.exit(2);
        }
        Path binary = Path.of(args[0]);
        Workspace ws = new Workspace(Path.of(args[1]));

        String envDir = System.getenv("GHIDRA_INSTALL_DIR");
        if (envDir == null) {
            System.err.println("GHIDRA_INSTALL_DIR not set");
            System.exit(2);
        }
        GhidraInstall install = GhidraInstall.at(Path.of(envDir));
        HeadlessGhidraEngine engine = new HeadlessGhidraEngine(install, ws, line -> {});

        System.out.println("== import ==");
        GuProject project = ws.importBinary(binary, null);
        System.out.println("project " + project.id() + " sha=" + project.sha256());

        System.out.println("== analyze ==");
        AnalysisResult r = engine.analyze(project);
        System.out.printf("functions=%d decompiled=%d strings=%d imports=%d callGraph=%d%n",
                r.functions().size(), r.decompiled().size(), r.strings().size(),
                r.imports().size(), r.callGraph().size());

        FunctionInfo add = find(r, "add");
        check("read: function 'add' present", add != null);
        check("read: decompilation non-empty",
                r.decompiled().stream().anyMatch(d -> !d.code().isBlank()));
        check("read: call graph has main->compute",
                r.callGraph().stream().anyMatch(e ->
                        e.callerName().equals("main") && e.calleeName().equals("compute")));
        check("read: disassembly present with instructions",
                r.disassembly() != null && r.disassembly().stream()
                        .anyMatch(d -> d.instructions() != null && !d.instructions().isEmpty()));

        if (add != null) {
            System.out.println("== rename add -> renamed_add ==");
            engine.renameFunction(project, add.address(), "renamed_add");
            AnalysisResult r2 = engine.load(project);
            check("write: rename reflected after re-export",
                    find(r2, "renamed_add") != null && find(r2, "add") == null);

            System.out.println("== comment on renamed_add ==");
            engine.setComment(project, add.address(), "GU: identified as integer add(a,b)");
            check("write: setComment did not throw", true);

            System.out.println("== retype (best effort) ==");
            try {
                engine.retypeVariable(project, add.address(), "param_1", "int");
                System.out.println("retype applied (or no-op)");
            } catch (GhidraEngine.EngineException e) {
                System.out.println("retype best-effort note: " + e.getMessage());
            }
        }

        System.out.println("ALL CHECKS DONE");
    }

    private static FunctionInfo find(AnalysisResult r, String name) {
        Optional<FunctionInfo> f = r.functions().stream()
                .filter(x -> x.name().equals(name)).findFirst();
        return f.orElse(null);
    }

    private static void check(String label, boolean ok) {
        System.out.println((ok ? "PASS " : "FAIL ") + label);
        if (!ok) {
            throw new AssertionError(label);
        }
    }

    private EngineRunner() {}
}
