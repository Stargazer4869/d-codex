package org.dean.codex.runtime.springai;

/**
 * Preloads Netty classes that may otherwise be resolved late on Reactor shutdown.
 *
 * <p>When the packaged Spring Boot app runs from nested jars, Netty can defer loading
 * {@code io.netty.buffer.FreeChunkEvent} until event-loop teardown. Preloading it during
 * startup avoids a shutdown-time class lookup from the launched nested-jar class loader.</p>
 */
public final class NettyRuntimeWarmup {

    private static final String[] SHUTDOWN_SENSITIVE_CLASS_NAMES = {
            "io.netty.buffer.FreeChunkEvent"
    };

    private NettyRuntimeWarmup() {
    }

    public static void preloadShutdownSensitiveNettyClasses() {
        ClassLoader classLoader = NettyRuntimeWarmup.class.getClassLoader();
        for (String className : SHUTDOWN_SENSITIVE_CLASS_NAMES) {
            try {
                Class.forName(className, true, classLoader);
            }
            catch (Throwable ignored) {
                // Best-effort warmup only. If the class is absent in a future Netty build,
                // startup should continue and the runtime can rely on the normal class path.
            }
        }
    }
}
