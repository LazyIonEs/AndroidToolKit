import com.android.apksig.ApkSigner;
import com.android.apksig.KeyConfig;
import com.android.ide.common.signing.KeystoreHelper;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

public class Phase4BFixtures {
 public static void main(String[] args) throws Exception {
  var directory = Path.of(args[0]);
  var store = directory.resolve("双别名 fixture.jks").toFile();
  for (var alias: List.of("first", "second")) {
   if (!KeystoreHelper.createNewStore("JKS", store, "fixture-only", "fixture-only", alias,
    "CN=Phase4B " + alias + ",OU=Test,O=AndroidToolKit,L=Shanghai,S=Shanghai,C=CN", 1, 2048)) throw new AssertionError();
  }
  var info = KeystoreHelper.getCertificateInfo("JKS", store, "fixture-only", "fixture-only", "first");
  var config = new ApkSigner.SignerConfig.Builder("FIRST", new KeyConfig.Jca(info.getKey()), List.of(info.getCertificate())).build();
  var unsigned = directory.resolve("unsigned.apk").toFile();
  try (var source = new ZipFile(args[1]); var output = new ZipOutputStream(new FileOutputStream(unsigned))) {
   for (var entry: Collections.list(source.entries())) {
    if (entry.getName().startsWith("META-INF/")) continue;
    output.putNextEntry(new ZipEntry(entry.getName()));
    try (var input = source.getInputStream(entry)) { input.transferTo(output); }
    output.closeEntry();
   }
  }
  var signed = directory.resolve("已签名 fixture.apk").toFile();
  new ApkSigner.Builder(List.of(config)).setInputApk(unsigned).setOutputApk(signed)
   .setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(true).setV4SigningEnabled(false).build().sign();
  Files.writeString(directory.resolve("damaged.apk"), "not an apk");
  System.out.println("directory=" + directory);
  System.out.println("keystore=" + store);
  System.out.println("apk=" + signed);
  for (var digest: List.of("MD5", "SHA-1", "SHA-256")) {
   var bytes = MessageDigest.getInstance(digest).digest(info.getCertificate().getEncoded());
   System.out.println(digest+"="+HexFormat.ofDelimiter(":").withUpperCase().formatHex(bytes));
  }
 }
}
