package io.ghidrauniversal.core.model;

/**
 * An imported (external) symbol the program depends on.
 *
 * @param name    imported symbol name
 * @param library source library/module, when known (e.g. {@code "KERNEL32.DLL"})
 * @param address thunk/IAT address, hex string, when known
 */
public record ImportInfo(String name, String library, String address) {}
