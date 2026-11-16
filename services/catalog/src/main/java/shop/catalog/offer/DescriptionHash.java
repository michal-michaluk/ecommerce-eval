package shop.catalog.offer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * D39 — the {@code descriptionHash} 13 promises over the frozen value is computed at the contract boundary, never in the model: a
 * domain type may not import a tooling dependency (`model.no-tools-dependency`), and the value it would hash is the one the outbound
 * adapter has already rendered.
 *
 * <p>The input is the JSON of the value, so equal content gives equal input and therefore equal hash, and one changed component
 * changes it. The output names the algorithm, because a bare hex digest is unreadable to a consumer that wants to recompute it.
 */
final class DescriptionHash {

    private static final String ALGORITHM = "SHA-256";
    private static final String PREFIX = "sha256:";

    private DescriptionHash() {
    }

    static String of(String valueJson) {
        return PREFIX + HexFormat.of().formatHex(digestOf(valueJson));
    }

    private static byte[] digestOf(String valueJson) {
        try {
            return MessageDigest.getInstance(ALGORITHM).digest(valueJson.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException unsupported) {
            throw new IllegalStateException(ALGORITHM + " is required by the 13 contract", unsupported);
        }
    }
}
