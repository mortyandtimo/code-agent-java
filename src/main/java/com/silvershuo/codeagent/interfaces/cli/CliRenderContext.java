package com.silvershuo.codeagent.interfaces.cli;

public final class CliRenderContext {

    private static final ThreadLocal<CliRenderer> CURRENT = new ThreadLocal<CliRenderer>();

    private CliRenderContext() {
    }

    public static void set(CliRenderer renderer) {
        CURRENT.set(renderer);
    }

    public static CliRenderer get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
