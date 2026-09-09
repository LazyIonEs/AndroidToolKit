package migration.smoke;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;

/** Runs against the actual joined ProGuard JAR using the packaged Java runtime. */
public final class ReleaseNativeSmoke {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        Path input = output.resolve("native-input.png");
        Path resized = output.resolve("native-output.png");
        BufferedImage pixels = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            pixels.setRGB(x, y, 0xff00007f | ((x * 17) << 16) | ((y * 17) << 8));
        }
        if (!ImageIO.write(pixels, "png", input.toFile())) throw new AssertionError("PNG encoder");
        // UInt parameters have a Kotlin-mangled JVM name; discover the retained binding.
        var method = Arrays.stream(Class.forName("uniffi.toolkit.ToolkitKt").getMethods())
            .filter(m -> m.getName().startsWith("resizePng-")).findFirst().orElseThrow();
        method.invoke(null, input.toString(), resized.toString(), 48, 48, (byte) 3);
        BufferedImage result = ImageIO.read(resized.toFile());
        if (result == null || result.getWidth() != 48 || result.getHeight() != 48) {
            throw new AssertionError("Native resize did not produce 48x48 PNG");
        }
        System.out.println("PASS: ProGuard UniFFI/JNA loads bundled Rust library and produces 48x48 PNG");
    }
}
