package io.ghidrauniversal.core.model;

/**
 * A defined string in the program.
 *
 * @param address address of the string data, hex string
 * @param value   the string contents
 * @param type    Ghidra data type (e.g. {@code "string"}, {@code "unicode"})
 */
public record StringInfo(String address, String value, String type) {}
