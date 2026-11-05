package io.ghidrauniversal.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * The complete JSON document emitted by the Ghidra export script after
 * auto-analysis, and consumed by the engine/MCP layers.
 *
 * <p>Decompiled C is carried per-function here so a single export pass
 * populates the whole model; large binaries may instead stream decompilation
 * on demand in a later phase.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalysisResult(
        ProgramInfo program,
        List<FunctionInfo> functions,
        List<DecompiledFunction> decompiled,
        List<Disassembly> disassembly,
        List<StringInfo> strings,
        List<ImportInfo> imports,
        List<ExportInfo> exports,
        List<SymbolInfo> symbols,
        List<XrefInfo> xrefs,
        List<CallGraphEdge> callGraph) {

    /** Schema version so the engine can reject incompatible export output. */
    public static final int SCHEMA_VERSION = 1;
}
