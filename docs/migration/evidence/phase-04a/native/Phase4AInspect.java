import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.security.interfaces.*;
import java.util.*;
class Phase4AInspect {
    public static void main(String[] args) throws Exception {
        var path = Path.of(args[0]);
        var store = KeyStore.getInstance(path.toFile(), "phase4a-fixture-pass".toCharArray());
        var aliases = Collections.list(store.aliases());
        if (!aliases.equals(List.of("phase4a-fixture"))) throw new AssertionError(aliases);
        var key = (RSAPrivateKey) store.getKey("phase4a-fixture", "phase4a-alias-pass".toCharArray());
        var certificate = (X509Certificate) store.getCertificate("phase4a-fixture");
        certificate.checkValidity();
        certificate.verify(certificate.getPublicKey());
        long days = (certificate.getNotAfter().getTime() - certificate.getNotBefore().getTime()) / 86400000L;
        if (key.getModulus().bitLength() != 2048 || days != 730) throw new AssertionError();
        System.out.printf("{\"format\":\"%s\",\"alias\":\"phase4a-fixture\",\"privateKeyReadable\":true,\"rsaBits\":2048,\"validityDays\":730,\"signature\":\"%s\",\"selfSignatureVerified\":true,\"bytes\":%d}%n", store.getType(), certificate.getSigAlgName(), Files.size(path));
    }
}
