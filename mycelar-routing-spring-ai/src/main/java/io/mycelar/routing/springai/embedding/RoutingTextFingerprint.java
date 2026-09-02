package io.mycelar.routing.springai.embedding;

public interface RoutingTextFingerprint {
    String fingerprint(String encoded);

    static RoutingTextFingerprint sha256() {
        return new Sha256RoutingTextFingerprint();
    }
}
