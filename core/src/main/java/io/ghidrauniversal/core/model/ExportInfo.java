package io.ghidrauniversal.core.model;

/**
 * An exported symbol the program exposes to others.
 *
 * @param name    exported symbol name
 * @param address address, hex string
 */
public record ExportInfo(String name, String address) {}
