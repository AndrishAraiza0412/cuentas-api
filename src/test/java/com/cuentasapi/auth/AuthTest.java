package com.cuentasapi.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.cuentasapi.IntegrationTest;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Instant;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class AuthTest extends IntegrationTest {

    @Autowired
    JwtDecoder jwtDecoder;

    @Value("${app.jwt.secret}")
    String secret;

    private ResponseEntity<Map> register(String email, String password) {
        return rest.postForEntity("/api/v1/auth/register", Map.of("email", email, "password", password), Map.class);
    }

    private ResponseEntity<Map> login(String email, String password) {
        return rest.postForEntity("/api/v1/auth/login", Map.of("email", email, "password", password), Map.class);
    }

    @Test
    void register_creates_a_cliente_and_login_returns_a_token() {
        ResponseEntity<Map> created = register("ana@x.com", "correct-horse-1");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("role")).isEqualTo("CLIENTE");

        ResponseEntity<Map> logged = login("ana@x.com", "correct-horse-1");
        assertThat(logged.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = (String) logged.getBody().get("token");
        assertThat(jwtDecoder.decode(token).getClaimAsString("role")).isEqualTo("CLIENTE");
    }

    @Test
    void register_twice_with_different_letter_case_is_409() {
        register("A@x.com", "correct-horse-1");

        ResponseEntity<Map> second = register("a@X.com", "correct-horse-1");

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(1);
    }

    @Test
    void short_password_is_400() {
        assertThat(register("b@x.com", "short").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void wrong_password_and_unknown_email_are_401_with_the_same_body() {
        register("c@x.com", "correct-horse-1");

        ResponseEntity<Map> wrong = login("c@x.com", "not-the-password");
        ResponseEntity<Map> unknown = login("nobody@x.com", "correct-horse-1");

        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrong.getBody()).isEqualTo(unknown.getBody());
    }

    @Test
    void protected_route_without_token_is_401_problem_json() {
        ResponseEntity<Map> response = call(HttpMethod.GET, "/api/v1/accounts", null, null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
        assertThat(response.getBody().get("status")).isEqualTo(401);
    }

    @Test
    void tampered_token_is_401() {
        String token = clientToken("d@x.com");
        String[] parts = token.split("\\.");
        char[] signature = parts[2].toCharArray();
        signature[10] = signature[10] == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1] + "." + new String(signature);

        ResponseEntity<Map> response = call(HttpMethod.GET, "/api/v1/accounts", tampered, null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void expired_token_is_401() {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(
                new ImmutableSecret<>(new SecretKeySpec(secret.getBytes(), "HmacSHA256")));
        Instant past = Instant.now().minusSeconds(3600);
        String expired = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject("1").claim("role", "CLIENTE")
                        .issuedAt(past.minusSeconds(900)).expiresAt(past).build())).getTokenValue();

        ResponseEntity<Map> response = call(HttpMethod.GET, "/api/v1/accounts", expired, null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("status")).isEqualTo(401);
    }

    @Test
    void cliente_cannot_reach_operator_route_403() {
        ResponseEntity<Map> response = call(HttpMethod.GET, "/api/v1/audit", clientToken("e@x.com"), null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("status")).isEqualTo(403);
    }

    @Test
    void operator_is_seeded_idempotently_and_gets_the_operator_role() {
        operatorSeeder.seed("op@bank.test", "operator-pass-1");
        operatorSeeder.seed("op@bank.test", "operator-pass-1");

        ResponseEntity<Map> logged = login("op@bank.test", "operator-pass-1");

        assertThat(logged.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jwtDecoder.decode((String) logged.getBody().get("token")).getClaimAsString("role"))
                .isEqualTo("OPERADOR");
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(1);
    }
}
