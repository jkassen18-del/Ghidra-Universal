package io.ghidrauniversal.core.model;

/**
 * A single disassembled instruction.
 *
 * @param address  instruction address, hex string
 * @param mnemonic instruction mnemonic (e.g. {@code "MOV"})
 * @param operands operand text (e.g. {@code "EAX, dword ptr [EBP + 0x8]"})
 * @param bytes    raw instruction bytes, hex string (e.g. {@code "8b 45 08"})
 */
public record Instruction(String address, String mnemonic, String operands, String bytes) {}
