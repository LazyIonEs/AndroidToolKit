package migration.smoke;

import java.awt.EventQueue;
import java.io.BufferedWriter;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Opt-in observer for a disposable optimized release, never shipped in the application. */
public final class ReleaseProfileAgent {
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
    private static final AtomicBoolean PENDING = new AtomicBoolean();
    private static final AtomicBoolean CLOSED = new AtomicBoolean();
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "migration-release-observer"); t.setDaemon(true); return t;
    });
    private static BufferedWriter samples;
    private static BufferedWriter probes;
    private static Path directory;
    private static volatile String scenario = "startup";
    private static volatile long edt = -1;
    private static long tick;

    public static void premain(String args, Instrumentation unused) throws Exception {
        directory = Path.of(args).toAbsolutePath();
        Files.createDirectories(directory);
        Files.deleteIfExists(directory.resolve("complete.txt"));
        samples = Files.newBufferedWriter(directory.resolve("edt-samples.jsonl"));
        probes = Files.newBufferedWriter(directory.resolve("event-queue-probes.jsonl"));
        Runtime.getRuntime().addShutdownHook(new Thread(ReleaseProfileAgent::close, "migration-observer-close"));
        TIMER.scheduleAtFixedRate(ReleaseProfileAgent::observe, 0, 50, TimeUnit.MILLISECONDS);
    }

    private static void observe() {
        try {
            Path marker = directory.resolve("scenario.txt");
            if (Files.isRegularFile(marker)) scenario = Files.readString(marker).strip();
            if (scenario.equals("stop")) { close(); return; }
            long wall = System.currentTimeMillis();
            if (PENDING.compareAndSet(false, true)) {
                long sent = System.nanoTime();
                String label = scenario;
                EventQueue.invokeLater(() -> {
                    edt = Thread.currentThread().threadId();
                    double millis = (System.nanoTime() - sent) / 1_000_000.0;
                    synchronized (ReleaseProfileAgent.class) {
                        if (!CLOSED.get()) try {
                            probes.write("{\"time\":" + wall + ",\"scenario\":" + quote(label)
                                + ",\"latencyMs\":" + millis + "}\n");
                        } catch (Exception failure) { failure.printStackTrace(); }
                    }
                    PENDING.set(false);
                });
            }
            ThreadInfo info = edt < 0 ? null : THREADS.getThreadInfo(edt, 96);
            synchronized (ReleaseProfileAgent.class) {
                if (CLOSED.get()) return;
                if (info != null) {
                    StringBuilder stack = new StringBuilder("[");
                    for (StackTraceElement frame : info.getStackTrace()) {
                        if (stack.length() > 1) stack.append(',');
                        stack.append(quote(frame.toString()));
                    }
                    stack.append(']');
                    samples.write("{\"time\":" + wall + ",\"scenario\":" + quote(scenario)
                        + ",\"thread\":" + quote(info.getThreadName())
                        + ",\"state\":" + quote(info.getThreadState().name())
                        + ",\"heapUsed\":" + ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed()
                        + ",\"threadCount\":" + THREADS.getThreadCount()
                        + ",\"stack\":" + stack + "}\n");
                }
                if (++tick % 20 == 0) { samples.flush(); probes.flush(); }
            }
        } catch (Exception failure) { failure.printStackTrace(); close(); }
    }

    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    private static synchronized void close() {
        if (!CLOSED.compareAndSet(false, true)) return;
        TIMER.shutdown();
        try { samples.close(); probes.close(); Files.writeString(directory.resolve("complete.txt"), "observer closed\n"); }
        catch (Exception failure) { failure.printStackTrace(); }
    }
}
