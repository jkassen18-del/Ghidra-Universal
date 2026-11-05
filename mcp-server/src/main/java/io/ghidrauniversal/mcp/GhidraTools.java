package io.ghidrauniversal.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.ghidrauniversal.core.Json;
import io.ghidrauniversal.core.model.AnalysisResult;
import io.ghidrauniversal.core.model.CallGraphEdge;
import io.ghidrauniversal.core.model.Disassembly;
import io.ghidrauniversal.core.model.FunctionInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds the MCP tool set over an {@link AnalysisContext}. */
public final class GhidraTools {

    private static final ObjectMapper M = Json.mapper();
    private final AnalysisContext ctx;

    public GhidraTools(AnalysisContext ctx) {
        this.ctx = ctx;
    }

    public List<McpTool> all() {
        List<McpTool> tools = new ArrayList<>();

        tools.add(tool("get_project_info",
                "Metadata about the analyzed program: name, format, processor, image base, SHA-256, function count.",
                schema(),
                args -> Json.write(ctx.analysis().program())));

        tools.add(tool("list_functions",
                "List discovered functions (name, address, size, signature). Optional 'query' substring filter and 'limit'.",
                schema(opt("query", "case-insensitive substring to match in the function name"),
                        opt("limit", "maximum number of functions to return")),
                args -> {
                    String q = str(args, "query");
                    int limit = intArg(args, "limit", Integer.MAX_VALUE);
                    List<FunctionInfo> out = new ArrayList<>();
                    for (FunctionInfo f : ctx.analysis().functions()) {
                        if (q == null || f.name().toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))) {
                            out.add(f);
                            if (out.size() >= limit) {
                                break;
                            }
                        }
                    }
                    return Json.write(out);
                }));

        tools.add(tool("get_decompiled_c",
                "Decompiled C for one function, resolved by name or address via 'function'.",
                schema(req("function", "function name or entry-point address")),
                args -> {
                    String key = reqStr(args, "function");
                    return ctx.analysis().decompiled().stream()
                            .filter(d -> d.name().equals(key) || d.address().equalsIgnoreCase(key))
                            .findFirst()
                            .map(Json::write)
                            .orElse("{\"error\":\"no decompiled function matching '" + key + "'\"}");
                }));

        tools.add(tool("get_disassembly",
                "Instruction listing for one function, resolved by name or address via 'function'.",
                schema(req("function", "function name or entry-point address")),
                args -> {
                    String key = reqStr(args, "function");
                    return ctx.analysis().disassembly().stream()
                            .filter(d -> d.function().equals(key) || d.address().equalsIgnoreCase(key))
                            .findFirst()
                            .map(Json::write)
                            .orElse("{\"error\":\"no disassembly for '" + key + "'\"}");
                }));

        tools.add(tool("get_strings",
                "Defined strings and their addresses. Optional 'query' substring filter.",
                schema(opt("query", "case-insensitive substring to match in the string value")),
                args -> {
                    String q = str(args, "query");
                    if (q == null) {
                        return Json.write(ctx.analysis().strings());
                    }
                    String ql = q.toLowerCase(Locale.ROOT);
                    return Json.write(ctx.analysis().strings().stream()
                            .filter(s -> s.value().toLowerCase(Locale.ROOT).contains(ql))
                            .toList());
                }));

        tools.add(tool("get_imports",
                "Imported (external) symbols the program depends on.",
                schema(),
                args -> Json.write(ctx.analysis().imports())));

        tools.add(tool("get_exports",
                "Exported symbols / external entry points.",
                schema(),
                args -> Json.write(ctx.analysis().exports())));

        tools.add(tool("get_symbols",
                "Program symbol table. Optional 'query' substring filter.",
                schema(opt("query", "case-insensitive substring to match in the symbol name")),
                args -> {
                    String q = str(args, "query");
                    if (q == null) {
                        return Json.write(ctx.analysis().symbols());
                    }
                    String ql = q.toLowerCase(Locale.ROOT);
                    return Json.write(ctx.analysis().symbols().stream()
                            .filter(s -> s.name().toLowerCase(Locale.ROOT).contains(ql))
                            .toList());
                }));

        tools.add(tool("get_xrefs",
                "Cross-references touching an address (as source or target), via 'address'.",
                schema(req("address", "address, hex string")),
                args -> {
                    String addr = reqStr(args, "address");
                    return Json.write(ctx.analysis().xrefs().stream()
                            .filter(x -> x.fromAddress().equalsIgnoreCase(addr)
                                    || x.toAddress().equalsIgnoreCase(addr))
                            .toList());
                }));

        tools.add(tool("get_call_graph",
                "Caller/callee edges. Optional 'function' (name or address) filters to edges touching it.",
                schema(opt("function", "function name or address to filter on")),
                args -> {
                    String key = str(args, "function");
                    if (key == null) {
                        return Json.write(ctx.analysis().callGraph());
                    }
                    return Json.write(ctx.analysis().callGraph().stream()
                            .filter(e -> touches(e, key))
                            .toList());
                }));

        tools.add(tool("search",
                "Search functions, symbols and strings for a substring via 'query'.",
                schema(req("query", "case-insensitive substring")),
                args -> {
                    String q = reqStr(args, "query").toLowerCase(Locale.ROOT);
                    AnalysisResult a = ctx.analysis();
                    ObjectNode res = M.createObjectNode();
                    res.set("functions", M.valueToTree(a.functions().stream()
                            .filter(f -> f.name().toLowerCase(Locale.ROOT).contains(q)).toList()));
                    res.set("symbols", M.valueToTree(a.symbols().stream()
                            .filter(s -> s.name().toLowerCase(Locale.ROOT).contains(q)).toList()));
                    res.set("strings", M.valueToTree(a.strings().stream()
                            .filter(s -> s.value().toLowerCase(Locale.ROOT).contains(q)).toList()));
                    return Json.write(res);
                }));

        // --- write-back ---

        tools.add(tool("rename_function",
                "Rename a function in the Ghidra project via 'address' and 'newName'.",
                schema(req("address", "function entry-point address"),
                        req("newName", "new function name")),
                args -> {
                    ctx.engine().renameFunction(ctx.project(), reqStr(args, "address"), reqStr(args, "newName"));
                    ctx.reload();
                    return "{\"ok\":true}";
                }));

        tools.add(tool("set_comment",
                "Set an EOL comment at an address via 'address' and 'comment'.",
                schema(req("address", "address, hex string"),
                        req("comment", "comment text")),
                args -> {
                    ctx.engine().setComment(ctx.project(), reqStr(args, "address"), reqStr(args, "comment"));
                    ctx.reload();
                    return "{\"ok\":true}";
                }));

        tools.add(tool("retype_variable",
                "Change a variable's type in a function via 'function' (address), 'variable' and 'type'.",
                schema(req("function", "function entry-point address"),
                        req("variable", "variable name"),
                        req("type", "C-like type name, e.g. int, char *, uint")),
                args -> {
                    ctx.engine().retypeVariable(ctx.project(), reqStr(args, "function"),
                            reqStr(args, "variable"), reqStr(args, "type"));
                    ctx.reload();
                    return "{\"ok\":true}";
                }));

        return tools;
    }

    private static boolean touches(CallGraphEdge e, String key) {
        return e.callerName().equals(key) || e.calleeName().equals(key)
                || e.callerAddress().equalsIgnoreCase(key) || e.calleeAddress().equalsIgnoreCase(key);
    }

    // --- tool + schema construction helpers ---

    private interface Handler {
        String apply(JsonNode args) throws Exception;
    }

    private static McpTool tool(String name, String description, ObjectNode schema, Handler handler) {
        return new McpTool() {
            @Override public String name() { return name; }
            @Override public String description() { return description; }
            @Override public ObjectNode inputSchema() { return schema.deepCopy(); }
            @Override public String call(JsonNode arguments) throws Exception {
                return handler.apply(arguments != null ? arguments : M.createObjectNode());
            }
        };
    }

    private record Param(String name, String description, boolean required) {}

    private static Param req(String name, String desc) { return new Param(name, desc, true); }
    private static Param opt(String name, String desc) { return new Param(name, desc, false); }

    private static ObjectNode schema(Param... params) {
        ObjectNode schema = M.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ArrayNode required = M.createArrayNode();
        for (Param p : params) {
            ObjectNode prop = props.putObject(p.name());
            prop.put("type", "string");
            prop.put("description", p.description());
            if (p.required()) {
                required.add(p.name());
            }
        }
        if (!required.isEmpty()) {
            schema.set("required", required);
        }
        return schema;
    }

    private static String str(JsonNode args, String name) {
        JsonNode n = args.get(name);
        return n != null && !n.isNull() ? n.asText() : null;
    }

    private static String reqStr(JsonNode args, String name) {
        String v = str(args, name);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("missing required argument: " + name);
        }
        return v;
    }

    private static int intArg(JsonNode args, String name, int def) {
        JsonNode n = args.get(name);
        return n != null && n.isNumber() ? n.asInt()
                : (n != null && n.isTextual() ? tryParse(n.asText(), def) : def);
    }

    private static int tryParse(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
