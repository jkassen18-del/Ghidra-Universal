package io.ghidrauniversal.core.model;

/**
 * Decompiler output for one function.
 *
 * @param name    function name
 * @param address entry-point address, hex string
 * @param code    C-like source produced by the Ghidra decompiler
 */
public record DecompiledFunction(String name, String address, String code) {}
