// Ghidra-Universal write-back script.
//
// Applies a single annotation to the persistent Ghidra project, then lets the
// headless analyzer save the project. Invoked as a -postScript with -process
// (no re-analysis). Arguments:
//
//   rename  <address> <newName>
//   comment <address> <commentText>
//   retype  <functionAddress> <variableName> <typeName>
//
// Shipped as a resource and compiled by Ghidra at runtime (Ghidra flat API
// only, no external dependencies).
//
// @category Ghidra-Universal

import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.data.DataType;
import ghidra.program.model.data.DataTypeManager;
import ghidra.program.model.data.PointerDataType;
import ghidra.program.model.listing.CodeUnit;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Variable;
import ghidra.program.model.symbol.SourceType;

import java.util.ArrayList;
import java.util.List;

public class WriteAnnotation extends GhidraScript {

    @Override
    protected void run() throws Exception {
        String[] args = getScriptArgs();
        if (args.length < 1) {
            printerr("WriteAnnotation: missing operation");
            return;
        }
        String op = args[0];
        int tx = currentProgram.startTransaction("ghidra-universal-write");
        boolean ok = false;
        try {
            switch (op) {
                case "rename" -> ok = rename(require(args, 1), require(args, 2));
                case "comment" -> ok = comment(require(args, 1), require(args, 2));
                case "retype" -> ok = retype(require(args, 1), require(args, 2), require(args, 3));
                default -> printerr("WriteAnnotation: unknown operation '" + op + "'");
            }
        } finally {
            currentProgram.endTransaction(tx, ok);
        }
        if (ok) {
            println("WriteAnnotation: " + op + " applied");
        } else {
            printerr("WriteAnnotation: " + op + " failed");
        }
    }

    private boolean rename(String addrStr, String newName) throws Exception {
        Address addr = addr(addrStr);
        Function f = getFunctionAt(addr);
        if (f == null) {
            printerr("no function at " + addrStr);
            return false;
        }
        f.setName(newName, SourceType.USER_DEFINED);
        return true;
    }

    private boolean comment(String addrStr, String text) {
        Address addr = addr(addrStr);
        currentProgram.getListing().setComment(addr, CodeUnit.EOL_COMMENT, text);
        return true;
    }

    private boolean retype(String funcAddr, String varName, String typeName) {
        Address addr = addr(funcAddr);
        Function f = getFunctionAt(addr);
        if (f == null) {
            printerr("no function at " + funcAddr);
            return false;
        }
        List<Variable> candidates = new ArrayList<>();
        for (Variable v : f.getAllVariables()) {
            if (v.getName().equals(varName)) {
                candidates.add(v);
            }
        }
        if (candidates.isEmpty()) {
            printerr("no variable '" + varName + "' in " + f.getName());
            return false;
        }
        DataType dt = resolveType(typeName);
        if (dt == null) {
            printerr("could not resolve type '" + typeName + "'");
            return false;
        }
        try {
            for (Variable v : candidates) {
                v.setDataType(dt, SourceType.USER_DEFINED);
            }
            return true;
        } catch (Exception e) {
            printerr("retype failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Resolve a C-ish type name to a Ghidra DataType. Handles trailing '*' as a
     * pointer, a curated set of common primitives, and a fallback search of the
     * program's data type manager by name. Best-effort by design.
     */
    private DataType resolveType(String typeName) {
        String t = typeName.trim();
        int stars = 0;
        while (t.endsWith("*")) {
            stars++;
            t = t.substring(0, t.length() - 1).trim();
        }
        DataType base = primitive(t);
        if (base == null) {
            base = findByName(t);
        }
        if (base == null) {
            return null;
        }
        for (int i = 0; i < stars; i++) {
            base = new PointerDataType(base);
        }
        return base;
    }

    private DataType primitive(String t) {
        DataTypeManager dtm = currentProgram.getDataTypeManager();
        String key = t.toLowerCase().replaceAll("\\s+", " ");
        String path = switch (key) {
            case "void" -> "/void";
            case "bool", "_bool" -> "/bool";
            case "char", "signed char" -> "/char";
            case "unsigned char", "uchar", "byte" -> "/byte";
            case "short", "short int" -> "/short";
            case "unsigned short", "ushort" -> "/ushort";
            case "int" -> "/int";
            case "unsigned", "unsigned int", "uint" -> "/uint";
            case "long" -> "/long";
            case "unsigned long", "ulong" -> "/ulong";
            case "long long", "longlong" -> "/longlong";
            case "unsigned long long" -> "/ulonglong";
            case "float" -> "/float";
            case "double" -> "/double";
            default -> null;
        };
        return path != null ? dtm.getDataType(path) : null;
    }

    private DataType findByName(String name) {
        DataTypeManager dtm = currentProgram.getDataTypeManager();
        DataType exact = dtm.getDataType("/" + name);
        if (exact != null) {
            return exact;
        }
        List<DataType> matches = new ArrayList<>();
        dtm.findDataTypes(name, matches);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private Address addr(String s) {
        return currentProgram.getAddressFactory().getAddress(s);
    }

    private static String require(String[] args, int i) {
        if (i >= args.length) {
            throw new IllegalArgumentException("missing argument " + i);
        }
        return args[i];
    }
}
