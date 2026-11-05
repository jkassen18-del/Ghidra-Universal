package io.ghidrauniversal.mcp;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * One connected SSE client. The GET stream handler drains {@link #outbound};
 * POST handlers enqueue JSON-RPC response text to it.
 */
final class SseSession {

    private final String id;
    private final BlockingQueue<String> outbound = new LinkedBlockingQueue<>();
    private volatile boolean open = true;

    SseSession(String id) {
        this.id = id;
    }

    String id() {
        return id;
    }

    boolean isOpen() {
        return open;
    }

    void close() {
        open = false;
    }

    /** Queue a JSON-RPC message to be delivered over the stream. */
    void enqueue(String json) {
        outbound.add(json);
    }

    /** Wait up to {@code timeoutMs} for the next message; null on timeout. */
    String poll(long timeoutMs) throws InterruptedException {
        return outbound.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }
}
