package io.ghidrauniversal.core.model;

/**
 * Summary of a single function as discovered by Ghidra.
 *
 * @param name      function name (may be a Ghidra default like {@code FUN_00401000})
 * @param address   entry-point address, hex string (e.g. {@code "0x00401000"})
 * @param size      size in bytes of the function body
 * @param signature decompiler-inferred prototype, when available
 */
public record FunctionInfo(String name, String address, long size, String signature) {}
