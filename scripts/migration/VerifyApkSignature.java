package migration.smoke;

import com.android.apksig.ApkVerifier;
import java.io.File;
import java.util.List;

/** Independent verifier for APKs produced by the disposable native release UI. */
public final class VerifyApkSignature {
    public static void main(String[] args) throws Exception {
        String policy = args[0];
        File apk = new File(args[1]);
        List<Boolean> expected = switch (policy) {
            case "V1" -> List.of(true, false, false, false);
            case "V2" -> List.of(true, true, false, false);
            case "V2Only" -> List.of(false, true, false, false);
            case "V3" -> List.of(true, true, true, false);
            case "V4" -> List.of(true, true, true, true);
            default -> throw new IllegalArgumentException(policy);
        };
        ApkVerifier.Builder builder = new ApkVerifier.Builder(apk)
            .setMinCheckedPlatformVersion(policy.equals("V2Only") ? 24 : 21);
        if (policy.equals("V1")) builder.setMaxCheckedPlatformVersion(23);
        if (policy.equals("V4")) builder.setV4SignatureFile(new File(args[2]));
        ApkVerifier.Result result = builder.build().verify();
        List<Boolean> actual = List.of(result.isVerifiedUsingV1Scheme(), result.isVerifiedUsingV2Scheme(),
            result.isVerifiedUsingV3Scheme(), result.isVerifiedUsingV4Scheme());
        if (!result.isVerified() || !expected.equals(actual)) {
            throw new AssertionError(apk.getName() + ": " + actual + " " + result.getErrors());
        }
        System.out.println("PASS: " + apk.getName() + " " + policy + " " + actual);
    }
}
