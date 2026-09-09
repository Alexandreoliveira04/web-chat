package br.edu.webchat.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;


@Service
public class JwtService {

	private static final Logger log = LoggerFactory.getLogger(JwtService.class);

	private static final int MIN_SECRET_BYTES = 32;

	private final SecretKey key;
	private final Duration expiration;

	public JwtService(@Value("${jwt.secret}") String secret,
			@Value("${jwt.expiration:3600}") long expirationSeconds) {
		byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
		if (secretBytes.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException(
					"jwt.secret precisa ter no minimo " + MIN_SECRET_BYTES + " bytes para HS256");
		}

		this.key = Keys.hmacShaKeyFor(secretBytes);
		this.expiration = Duration.ofSeconds(expirationSeconds);
	}

	public String generateToken(String email) {
		Instant now = Instant.now();

		return Jwts.builder()
				.subject(email)
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(expiration)))
				.signWith(key)
				.compact();
	}

	public Optional<String> extractEmail(String token) {
		try {
			Claims claims = Jwts.parser()
					.verifyWith(key)
					.build()
					.parseSignedClaims(token)
					.getPayload();

			return Optional.ofNullable(claims.getSubject());
		}
		catch (JwtException | IllegalArgumentException ex) {
			log.debug("Token JWT rejeitado: {}", ex.getClass().getSimpleName());
			return Optional.empty();
		}
	}

	public long getExpirationSeconds() {
		return expiration.toSeconds();
	}

}
