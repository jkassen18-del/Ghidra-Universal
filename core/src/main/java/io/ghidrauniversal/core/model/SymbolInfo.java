package io.ghidrauniversal.core.model;

/**
 * A symbol in the program's symbol table.
 *
 * @param name      symbol name
 * @param address   address, hex string
 * @param type      symbol type (e.g. {@code "FUNCTION"}, {@code "LABEL"}, {@code "GLOBAL"})
 * @param namespace containing namespace, or {@code "Global"}
 */
public record SymbolInfo(String name, String address, String type, String namespace) {}
