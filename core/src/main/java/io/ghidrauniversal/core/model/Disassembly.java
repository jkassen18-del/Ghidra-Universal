package io.ghidrauniversal.core.model;

import java.util.List;

/**
 * Disassembly listing for one function.
 *
 * @param function     function name
 * @param address      entry-point address, hex string
 * @param instructions ordered instructions in the function body
 */
public record Disassembly(String function, String address, List<Instruction> instructions) {}
