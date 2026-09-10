package migration.smoke;

import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import javax.imageio.ImageIO;

/** Independent frozen iconGeneration pipeline and verifier for disposable release UI fixtures. */
public final class VerifyIconOutputs {
    private static final String[] DENSITIES = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
    private static final int[] SIZES = {48, 72, 96, 144, 192};
    private static final String FILE_DIR = " res 中文 ";

    public static void main(String[] args) throws Exception {
        if (args[0].equals("reference")) {
            reference(Path.of(args[1]), Path.of(args[2]), Boolean.parseBoolean(args[3]), args.length > 4 ? Integer.parseInt(args[4]) : 70);
        } else if (args[0].equals("verify")) {
            verify(Path.of(args[1]), Path.of(args[2]), args[3]);
        } else throw new IllegalArgumentException("reference <input> <output> <lossless> [minimum], or verify <expected> <actual> <extension>");
    }

    private static void invoke(String name, Object... args) throws Exception {
        Method method = Arrays.stream(Class.forName("uniffi.toolkit.ToolkitKt").getMethods())
            .filter(m -> (m.getName().equals(name) || m.getName().startsWith(name + "-")) && m.getParameterCount() == args.length)
            .findFirst().orElseThrow();
        method.invoke(null, args);
    }

    private static Path output(Path root, String density, String extension) {
        return root.resolve(FILE_DIR).resolve("drawable-" + density).resolve("icon fixture." + extension);
    }

    // The same sequence and default settings as MainViewModel.iconGeneration at 498ef364.
    private static void reference(Path input, Path root, boolean lossless, int minimum) throws Exception {
        String extension = input.toString().endsWith(".png") ? "png" : "jpg";
        for (int i = 0; i < DENSITIES.length; i++) {
            Path target = output(root, DENSITIES[i], extension);
            Path resized = target.resolveSibling("icon fixture_resize." + extension);
            Files.createDirectories(target.getParent());
            Files.deleteIfExists(target); Files.deleteIfExists(resized);
            try {
                if (extension.equals("png")) {
                    invoke("resizePng", input.toAbsolutePath().toString(), resized.toAbsolutePath().toString(), SIZES[i], SIZES[i], (byte) 3);
                    if (lossless) invoke("oxipng", resized.toAbsolutePath().toString(), target.toAbsolutePath().toString(), (byte) 6);
                    else invoke("quantize", resized.toAbsolutePath().toString(), target.toAbsolutePath().toString(), (byte) minimum, (byte) 100, 1, (byte) 6);
                } else {
                    invoke("resizeFir", input.toAbsolutePath().toString(), resized.toAbsolutePath().toString(), SIZES[i], SIZES[i], (byte) 5);
                    invoke("mozJpeg", resized.toAbsolutePath().toString(), target.toAbsolutePath().toString(), lossless ? 100f : 85f);
                }
            } finally { Files.deleteIfExists(resized); }
        }
        System.out.println("PASS: independent " + extension + " reference, lossless=" + lossless + ", minimum=" + minimum);
    }

    private static void verify(Path expected, Path actual, String extension) throws Exception {
        for (int i = 0; i < DENSITIES.length; i++) {
            Path source = output(expected, DENSITIES[i], extension);
            Path result = output(actual, DENSITIES[i], extension);
            byte[] bytes = Files.readAllBytes(result);
            if (!Arrays.equals(Files.readAllBytes(source), bytes)) throw new AssertionError("Encoded image mismatch: " + result);
            BufferedImage a = ImageIO.read(source.toFile());
            BufferedImage b = ImageIO.read(result.toFile());
            int size = SIZES[i];
            if (b == null || b.getWidth() != size || b.getHeight() != size ||
                    !Arrays.equals(a.getRGB(0, 0, size, size, null, 0, size), b.getRGB(0, 0, size, size, null, 0, size))) {
                throw new AssertionError("Pixel or dimension mismatch: " + result);
            }
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            System.out.println("PASS\t" + DENSITIES[i] + "\t" + size + "x" + size + "\t" + bytes.length + "\t" + hash);
        }
        try (var files = Files.walk(actual)) {
            if (files.anyMatch(p -> p.getFileName().toString().contains("_resize"))) throw new AssertionError("Temporary output remains");
        }
    }
}
