package io.ghidrauniversal.core.model;

/**
 * High-level metadata about the analyzed program.
 *
 * @param name            original binary file name
 * @param format          executable format (e.g. {@code "Portable Executable (PE)"})
 * @param languageId      Ghidra language/processor id (e.g. {@code "x86:LE:64:default"})
 * @param compilerSpecId  compiler spec id (e.g. {@code "windows"}, {@code "gcc"})
 * @param imageBase       image base address, hex string
 * @param sizeBytes       size of the input binary in bytes
 * @param sha256          SHA-256 of the input binary
 * @param functionCount   number of functions discovered
 */
public record ProgramInfo(String name, String format, String languageId, String compilerSpecId,
                          String imageBase, long sizeBytes, String sha256, int functionCount) {}
