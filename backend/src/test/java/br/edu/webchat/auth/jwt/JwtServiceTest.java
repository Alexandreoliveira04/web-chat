package br.edu.webchat.auth.jwt;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

	private static final String SECRET = "segredo-de-teste-com-tamanho-suficiente-para-hs256";

	private final JwtService jwtService = new JwtService(SECRET, 3600);

	@Test
	void deveGerarTokenELerOEmailDeVolta() {
		String token = jwtService.generateToken("alexandre@email.com");

		assertThat(jwtService.extractEmail(token)).contains("alexandre@email.com");
	}

	@Test
	void deveRejeitarTokenExpirado() {
		JwtService expirado = new JwtService(SECRET, -60);

		String token = expirado.generateToken("alexandre@email.com");

		assertThat(expirado.extractEmail(token)).isEmpty();
	}

	@Test
	void deveRejeitarTokenAssinadoComOutroSegredo() {
		String token = new JwtService("outro-segredo-completamente-diferente-com-32b", 3600)
				.generateToken("alexandre@email.com");

		assertThat(jwtService.extractEmail(token)).isEmpty();
	}

	@Test
	void deveRejeitarPayloadAdulterado() {
		String[] partes = jwtService.generateToken("alexandre@email.com").split("\\.");
		String payloadForjado = Base64.getUrlEncoder().withoutPadding()
				.encodeToString("{\"sub\":\"invasor@email.com\"}".getBytes(StandardCharsets.UTF_8));

		String forjado = partes[0] + "." + payloadForjado + "." + partes[2];

		assertThat(jwtService.extractEmail(forjado)).isEmpty();
	}

	@Test
	void deveRejeitarTokenMalformado() {
		assertThat(jwtService.extractEmail("nao-e-um-jwt")).isEmpty();
		assertThat(jwtService.extractEmail("a.b.c")).isEmpty();
		assertThat(jwtService.extractEmail("")).isEmpty();
	}

	@Test
	void deveRecusarSegredoCurtoDemaisParaHs256() {
		assertThatThrownBy(() -> new JwtService("curto-demais", 3600))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("32 bytes");
	}

	@Test
	void deveExporAExpiracaoConfigurada() {
		assertThat(jwtService.getExpirationSeconds()).isEqualTo(3600);
	}

}
