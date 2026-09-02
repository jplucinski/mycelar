package io.mycelar.routing.springai.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class Sha256RoutingTextFingerprintTest {

    @Test
    void lowercaseHexSha256OfUtf8() throws Exception {
        String encoded = "Label:\nDraft";
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(encoded.getBytes(StandardCharsets.UTF_8));
        assertThat(new Sha256RoutingTextFingerprint().fingerprint(encoded))
                .isEqualTo(HexFormat.of().formatHex(digest));
    }

    @Test
    void differentTextDifferentFingerprint() {
        var fp = new Sha256RoutingTextFingerprint();
        assertThat(fp.fingerprint("a")).isNotEqualTo(fp.fingerprint("b"));
    }
}
