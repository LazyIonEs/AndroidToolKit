package migration.smoke;

import java.awt.EventQueue;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.jvm.functions.Function2;
import kotlin.jvm.functions.Function3;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.Dispatchers;

/** Real retained Apache5 transport and serialization, served only by a loopback fixture. */
public final class ReleaseUpdateSmoke {
    public static void main(String[] args) {
        try { run(args); }
        catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }

    private static void run(String[] args) throws Exception {
        Class<?> transport = Class.forName(args[0]);
        Method transportEntry = Arrays.stream(transport.getDeclaredMethods()).filter(m -> m.getParameterCount() == 5
            && m.getParameterTypes()[0] == kotlinx.coroutines.CoroutineDispatcher.class
            && m.getParameterTypes()[1] == String.class && m.getParameterTypes()[2] == java.io.File.class)
            .findFirst().orElseThrow();
        // ProGuard specializes this internal bridge's Continuation to its sole caller.
        // Invoke the retained repository interface instead of fabricating that continuation.
        Class<?> repositoryType = transportEntry.getParameterTypes()[4].getEnclosingClass();
        var repositoryConstructor = repositoryType.getDeclaredConstructors()[0];
        Class<?> dispatchersType = repositoryConstructor.getParameterTypes()[0];
        Object dispatchers = dispatchersType.getConstructor(kotlinx.coroutines.CoroutineDispatcher.class,
            kotlinx.coroutines.CoroutineDispatcher.class, kotlinx.coroutines.CoroutineDispatcher.class)
            .newInstance(Dispatchers.getIO(), Dispatchers.getDefault(), Dispatchers.getMain());
        Object repository = repositoryConstructor.newInstance(dispatchers);
        Method download = Arrays.stream(repositoryType.getMethods()).filter(m -> m.getParameterCount() == 4
            && m.getParameterTypes()[1] == String.class && m.getParameterTypes()[2] == Function3.class
            && m.getParameterTypes()[3] == Continuation.class).findFirst().orElseThrow();
        var assetConstructor = download.getParameterTypes()[0].getConstructor(String.class, String.class);
        Path directory = Path.of(args[3]);
        Files.createDirectories(directory);
        AtomicInteger progressCount = new AtomicInteger();
        Function3<Long, Long, Continuation<? super Unit>, Object> progress = (bytes, total, continuation) -> {
            if (EventQueue.isDispatchThread()) throw new AssertionError("Transport progress executed on EDT");
            progressCount.incrementAndGet();
            return Unit.INSTANCE;
        };
        for (String route : new String[]{"known", "unknown", "broken", "installer.dmg"}) {
            Path output = directory.resolve(route.equals("installer.dmg") ? "AndroidToolKit-macos-arm64.dmg" : route + ".bin");
            String url = args[2] + "/" + route;
            Object asset = assetConstructor.newInstance(output.getFileName().toString(), url);
            Object result = BuildersKt.runBlocking(Dispatchers.getMain(), (scope, continuation) -> {
                if (!EventQueue.isDispatchThread()) throw new AssertionError("Smoke must invoke the adapter from EDT");
                try { return download.invoke(repository, asset, directory.toString(), progress, continuation); }
                catch (ReflectiveOperationException failure) { throw new RuntimeException(failure); }
            });
            if (route.equals("broken")) {
                if (downloadedPath(result) != null || Files.exists(output)) throw new AssertionError("Partial download was not rejected/removed");
            } else {
                if (!output.toString().equals(downloadedPath(result))) throw new AssertionError("Download failed: " + route);
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(output)));
                if (!hash.equals(args[route.equals("installer.dmg") ? 5 : 4])) throw new AssertionError("Download hash differs: " + route);
            }
            System.out.println("PASS packaged update transport: " + route);
        }
        if (progressCount.get() == 0) throw new AssertionError("No real progress callbacks");
        Class<?> checkBlock = Class.forName(args[1]);
        var constructor = Arrays.stream(checkBlock.getDeclaredConstructors()).filter(c -> c.getParameterCount() == 2
            && c.getParameterTypes()[0] == String.class && c.getParameterTypes()[1] == Continuation.class)
            .findFirst().orElseThrow();
        constructor.setAccessible(true);
        @SuppressWarnings("unchecked")
        Function2<kotlinx.coroutines.CoroutineScope, Continuation<? super Object>, Object> block =
            (Function2<kotlinx.coroutines.CoroutineScope, Continuation<? super Object>, Object>)
                constructor.newInstance(args[2] + "/release", null);
        Object result = BuildersKt.runBlocking(Dispatchers.getIO(), block);
        if (!success(result)) throw new AssertionError("Release JSON did not deserialize in optimized runtime");
        System.out.println("PASS packaged update metadata serialization; off-EDT progress callbacks=" + progressCount.get());
        System.exit(0); // Finish the optional observer and the test-only Swing event queue.
    }

    private static boolean success(Object result) throws Exception {
        Method getter = Arrays.stream(result.getClass().getMethods()).filter(m -> m.getParameterCount() == 0
            && m.getReturnType() == boolean.class).findFirst().orElseThrow();
        return (Boolean) getter.invoke(result);
    }

    private static String downloadedPath(Object result) throws Exception {
        for (var field : result.getClass().getDeclaredFields()) {
            if (field.getType() == String.class && !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                return (String) field.get(result);
            }
        }
        return null;
    }
}
