package io.ghidrauniversal.core.model;

/**
 * A cross-reference between two addresses.
 *
 * @param fromAddress source address of the reference, hex string
 * @param toAddress   target address of the reference, hex string
 * @param type        reference type (e.g. {@code "CALL"}, {@code "READ"}, {@code "WRITE"}, {@code "DATA"})
 */
public record XrefInfo(String fromAddress, String toAddress, String type) {}
