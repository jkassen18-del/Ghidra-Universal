package io.ghidrauniversal.core.model;

/**
 * A directed edge in the call graph: {@code caller} calls {@code callee}.
 *
 * @param callerAddress entry-point address of the calling function, hex string
 * @param callerName    name of the calling function
 * @param calleeAddress entry-point address of the called function, hex string
 * @param calleeName    name of the called function
 */
public record CallGraphEdge(String callerAddress, String callerName,
                            String calleeAddress, String calleeName) {}
