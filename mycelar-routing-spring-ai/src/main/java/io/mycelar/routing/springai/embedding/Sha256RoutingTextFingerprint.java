package io.mycelar.routing.springai.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

final class Sha256RoutingTextFingerprint implements RoutingTextFingerprint {

    @Override
    public String fingerprint(String encoded) {
        Objects.requireNonNull(encoded, "encoded");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(encoded.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
