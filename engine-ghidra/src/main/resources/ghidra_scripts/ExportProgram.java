// Ghidra-Universal export script.
//
// Runs as a Ghidra -postScript after auto-analysis and writes the full
// AnalysisResult (see io.ghidrauniversal.core.model.AnalysisResult) as JSON to
// the path given in the first script argument.
//
// This file is NOT compiled by the Ghidra-Universal Gradle build. It is shipped
// as a resource and compiled by Ghidra at runtime, inside Ghidra's own
// classpath, so it uses only the Ghidra flat API + a hand-rolled JSON writer
// (no external JSON dependency).
//
// @category Ghidra-Universal

import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileOptions;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Data;
import ghidra.program.model.listing.DataIterator;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Instruction;
import ghidra.program.model.listing.InstructionIterator;
import ghidra.program.model.listing.Listing;
import ghidra.program.model.symbol.ExternalManager;
import ghidra.program.model.symbol.Reference;
import ghidra.program.model.symbol.ReferenceManager;
import ghidra.program.model.symbol.Symbol;
import ghidra.program.model.symbol.SymbolIterator;
import ghidra.program.model.symbol.SymbolTable;
import ghidra.program.model.symbol.SymbolType;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public class ExportProgram extends GhidraScript {

    // Guard rails so a huge binary can't produce an unbounded dump in v1.
    private static final int DECOMPILE_TIMEOUT_SECS = 30;
    private static final long MAX_FUNCTIONS = 20000;
    private static final long MAX_SYMBOLS = 100000;
    private static final long MAX_XREFS = 200000;
    private static final long MAX_INSTRUCTIONS = 500000;

    @Override
    protected void run() throws Exception {
        String[] args = getScriptArgs();
        if (args.length < 1) {
            printerr("ExportProgram: missing output path argument");
            return;
        }
        Path out = Path.of(args[0]);
        Files.createDirectories(out.toAbsolutePath().getParent());

        Json j = new Json();
        j.beginObject();
        j.name("schemaVersion").value(1);
        writeProgram(j);
        writeFunctionsAndDecompilation(j);
        writeDisassembly(j);
        writeStrings(j);
        writeImports(j);
        writeExports(j);
        writeSymbols(j);
        writeXrefs(j);
        writeCallGraph(j);
        j.endObject();

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8))) {
            w.write(j.toString());
        }
        println("ExportProgram: wrote " + out);
    }

    private void writeProgram(Json j) {
        j.name("program").beginObject();
        j.name("name").value(currentProgram.getName());
        j.name("format").value(currentProgram.getExecutableFormat());
        j.name("languageId").value(currentProgram.getLanguageID().getIdAsString());
        j.name("compilerSpecId").value(
                currentProgram.getCompilerSpec().getCompilerSpecID().getIdAsString());
        j.name("imageBase").value(currentProgram.getImageBase().toString());
        long size = 0;
        try {
            size = currentProgram.getExecutablePath() != null
                    ? Files.size(Path.of(currentProgram.getExecutablePath()))
                    : 0;
        } catch (Exception ignore) {
            // best effort
        }
        j.name("sizeBytes").value(size);
        String sha = currentProgram.getExecutableSHA256();
        j.name("sha256").value(sha != null ? sha : "");
        j.name("functionCount").value(
                currentProgram.getFunctionManager().getFunctionCount());
        j.endObject();
    }

    private void writeFunctionsAndDecompilation(Json j) {
        DecompInterface decomp = new DecompInterface();
        decomp.setOptions(new DecompileOptions());
        decomp.openProgram(currentProgram);

        Json funcs = new Json().beginArray();
        Json decs = new Json().beginArray();

        long count = 0;
        for (Function f : currentProgram.getFunctionManager().getFunctions(true)) {
            if (monitor.isCancelled() || count >= MAX_FUNCTIONS) {
                break;
            }
            count++;
            String addr = f.getEntryPoint().toString();

            funcs.beginObject();
            funcs.name("name").value(f.getName());
            funcs.name("address").value(addr);
            funcs.name("size").value(f.getBody().getNumAddresses());
            funcs.name("signature").value(f.getSignature(false).getPrototypeString());
            funcs.endObject();

            String code = decompile(decomp, f);
            if (code != null) {
                decs.beginObject();
                decs.name("name").value(f.getName());
                decs.name("address").value(addr);
                decs.name("code").value(code);
                decs.endObject();
            }
        }
        decomp.dispose();

        j.name("functions").raw(funcs.endArray().toString());
        j.name("decompiled").raw(decs.endArray().toString());
    }

    private String decompile(DecompInterface decomp, Function f) {
        try {
            DecompileResults res = decomp.decompileFunction(f, DECOMPILE_TIMEOUT_SECS, monitor);
            if (res != null && res.decompileCompleted() && res.getDecompiledFunction() != null) {
                return res.getDecompiledFunction().getC();
            }
        } catch (Exception e) {
            printerr("decompile failed for " + f.getName() + ": " + e.getMessage());
        }
        return null;
    }

    private void writeDisassembly(Json j) {
        j.name("disassembly").beginArray();
        Listing listing = currentProgram.getListing();
        long total = 0;
        for (Function f : currentProgram.getFunctionManager().getFunctions(true)) {
            if (monitor.isCancelled() || total >= MAX_INSTRUCTIONS) {
                break;
            }
            j.beginObject();
            j.name("function").value(f.getName());
            j.name("address").value(f.getEntryPoint().toString());
            j.name("instructions").beginArray();
            InstructionIterator it = listing.getInstructions(f.getBody(), true);
            while (it.hasNext() && total < MAX_INSTRUCTIONS) {
                Instruction ins = it.next();
                total++;
                j.beginObject();
                j.name("address").value(ins.getAddress().toString());
                j.name("mnemonic").value(ins.getMnemonicString());
                j.name("operands").value(operands(ins));
                j.name("bytes").value(bytesHex(ins));
                j.endObject();
            }
            j.endArray();
            j.endObject();
        }
        j.endArray();
    }

    private String operands(Instruction ins) {
        StringBuilder sb = new StringBuilder();
        int n = ins.getNumOperands();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(ins.getDefaultOperandRepresentation(i));
        }
        return sb.toString();
    }

    private String bytesHex(Instruction ins) {
        try {
            byte[] b = ins.getBytes();
            StringBuilder sb = new StringBuilder(b.length * 2);
            for (byte x : b) {
                sb.append(Character.forDigit((x >> 4) & 0xf, 16));
                sb.append(Character.forDigit(x & 0xf, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void writeStrings(Json j) {
        j.name("strings").beginArray();
        Listing listing = currentProgram.getListing();
        DataIterator it = listing.getDefinedData(true);
        while (it.hasNext() && !monitor.isCancelled()) {
            Data d = it.next();
            Object value = d.getValue();
            if (value instanceof String) {
                j.beginObject();
                j.name("address").value(d.getAddress().toString());
                j.name("value").value((String) value);
                j.name("type").value(d.getDataType().getName());
                j.endObject();
            }
        }
        j.endArray();
    }

    private void writeImports(Json j) {
        j.name("imports").beginArray();
        ExternalManager ext = currentProgram.getExternalManager();
        SymbolTable st = currentProgram.getSymbolTable();
        SymbolIterator externals = st.getExternalSymbols();
        while (externals.hasNext() && !monitor.isCancelled()) {
            Symbol s = externals.next();
            if (s.getSymbolType() != SymbolType.FUNCTION && s.getSymbolType() != SymbolType.LABEL) {
                continue;
            }
            j.beginObject();
            j.name("name").value(s.getName());
            String lib = s.getParentNamespace() != null ? s.getParentNamespace().getName() : "";
            j.name("library").value(lib);
            j.name("address").value(s.getAddress() != null ? s.getAddress().toString() : "");
            j.endObject();
        }
        j.endArray();
    }

    private void writeExports(Json j) {
        j.name("exports").beginArray();
        SymbolTable st = currentProgram.getSymbolTable();
        for (Address addr : st.getExternalEntryPointIterator()) {
            if (monitor.isCancelled()) {
                break;
            }
            Symbol sym = st.getPrimarySymbol(addr);
            j.beginObject();
            j.name("name").value(sym != null ? sym.getName() : addr.toString());
            j.name("address").value(addr.toString());
            j.endObject();
        }
        j.endArray();
    }

    private void writeSymbols(Json j) {
        j.name("symbols").beginArray();
        SymbolTable st = currentProgram.getSymbolTable();
        SymbolIterator it = st.getAllSymbols(true);
        long count = 0;
        while (it.hasNext() && !monitor.isCancelled() && count < MAX_SYMBOLS) {
            Symbol s = it.next();
            count++;
            j.beginObject();
            j.name("name").value(s.getName());
            j.name("address").value(s.getAddress() != null ? s.getAddress().toString() : "");
            j.name("type").value(s.getSymbolType().toString());
            j.name("namespace").value(
                    s.getParentNamespace() != null ? s.getParentNamespace().getName() : "Global");
            j.endObject();
        }
        j.endArray();
    }

    private void writeXrefs(Json j) {
        j.name("xrefs").beginArray();
        ReferenceManager rm = currentProgram.getReferenceManager();
        long count = 0;
        for (Address from : rm.getReferenceSourceIterator(
                currentProgram.getMemory(), true)) {
            if (monitor.isCancelled() || count >= MAX_XREFS) {
                break;
            }
            for (Reference ref : rm.getReferencesFrom(from)) {
                if (count >= MAX_XREFS) {
                    break;
                }
                count++;
                j.beginObject();
                j.name("fromAddress").value(ref.getFromAddress().toString());
                j.name("toAddress").value(ref.getToAddress().toString());
                j.name("type").value(ref.getReferenceType().getName());
                j.endObject();
            }
        }
        j.endArray();
    }

    private void writeCallGraph(Json j) {
        j.name("callGraph").beginArray();
        for (Function caller : currentProgram.getFunctionManager().getFunctions(true)) {
            if (monitor.isCancelled()) {
                break;
            }
            Set<Function> callees;
            try {
                callees = caller.getCalledFunctions(monitor);
            } catch (Exception e) {
                continue;
            }
            for (Function callee : callees) {
                j.beginObject();
                j.name("callerAddress").value(caller.getEntryPoint().toString());
                j.name("callerName").value(caller.getName());
                j.name("calleeAddress").value(callee.getEntryPoint().toString());
                j.name("calleeName").value(callee.getName());
                j.endObject();
            }
        }
        j.endArray();
    }

    // ---------------------------------------------------------------------
    // Minimal streaming JSON writer. No pretty-printing; the engine re-parses
    // and the GUI/MCP layers present it.
    // ---------------------------------------------------------------------
    private static final class Json {
        private final StringBuilder sb = new StringBuilder(1 << 16);
        private boolean needComma = false;

        Json beginObject() {
            preValue();
            sb.append('{');
            needComma = false;
            return this;
        }

        Json endObject() {
            sb.append('}');
            needComma = true;
            return this;
        }

        Json beginArray() {
            preValue();
            sb.append('[');
            needComma = false;
            return this;
        }

        Json endArray() {
            sb.append(']');
            needComma = true;
            return this;
        }

        Json name(String n) {
            if (needComma) {
                sb.append(',');
            }
            quote(n);
            sb.append(':');
            needComma = false;
            return this;
        }

        Json value(String v) {
            preValue();
            if (v == null) {
                sb.append("null");
            } else {
                quote(v);
            }
            needComma = true;
            return this;
        }

        Json value(long v) {
            preValue();
            sb.append(v);
            needComma = true;
            return this;
        }

        /** Insert already-serialized JSON as a value (e.g. a nested array). */
        Json raw(String json) {
            preValue();
            sb.append(json);
            needComma = true;
            return this;
        }

        private void preValue() {
            // Add a separating comma only between consecutive array/object elements.
            if (needComma && sb.length() > 0) {
                char last = sb.charAt(sb.length() - 1);
                if (last != ':' && last != '{' && last != '[') {
                    sb.append(',');
                }
            }
        }

        private void quote(String s) {
            sb.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    case '\b' -> sb.append("\\b");
                    case '\f' -> sb.append("\\f");
                    default -> {
                        if (c < 0x20) {
                            sb.append(String.format("\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                    }
                }
            }
            sb.append('"');
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }
}
