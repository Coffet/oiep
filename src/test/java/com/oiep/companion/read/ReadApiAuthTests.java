package com.oiep.companion.read;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bearer authentication for the read API: only a well-formed {@code Bearer} header carrying the
 * configured token is accepted; every other shape fails closed.
 */
class ReadApiAuthTests {

    private static final String TOKEN = "long-random-read-token-used-by-tests";

    private final ReadApiAuth auth = new ReadApiAuth(TOKEN);

    @Test
    void acceptsTheExactBearerToken() {
        assertThat(auth.isAuthorized("Bearer " + TOKEN)).isTrue();
        assertThat(auth.isAuthorized("bearer " + TOKEN)).isTrue();
        assertThat(auth.isAuthorized("BEARER " + TOKEN)).isTrue();
        assertThat(auth.isAuthorized("  Bearer " + TOKEN + "  ")).isTrue();
    }

    @Test
    void rejectsMissingMalformedAndWrongCredentials() {
        assertThat(auth.isAuthorized(null)).isFalse();
        assertThat(auth.isAuthorized("")).isFalse();
        assertThat(auth.isAuthorized("   ")).isFalse();
        assertThat(auth.isAuthorized(TOKEN)).isFalse();
        assertThat(auth.isAuthorized("Bearer")).isFalse();
        assertThat(auth.isAuthorized("Bearer ")).isFalse();
        assertThat(auth.isAuthorized("Bearer    ")).isFalse();
        assertThat(auth.isAuthorized("Token " + TOKEN)).isFalse();
        assertThat(auth.isAuthorized("Bearer " + TOKEN + "x")).isFalse();
        assertThat(auth.isAuthorized("Bearer x" + TOKEN)).isFalse();
        assertThat(auth.isAuthorized("Bearer different-token-of-same-len")).isFalse();
    }

    @Test
    void emptyConfiguredTokenNeverAuthorizes() {
        assertThat(new ReadApiAuth("").isAuthorized("Bearer ")).isFalse();
        assertThat(new ReadApiAuth("").isAuthorized("Bearer anything")).isFalse();
        assertThat(new ReadApiAuth(null).isAuthorized("Bearer ")).isFalse();
    }
}
