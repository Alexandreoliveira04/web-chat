package br.edu.webchat.call.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IceServersServiceTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void semConfiguracaoUsaSoOStunPublico() {
		IceServersService service = new IceServersService(objectMapper, "");

		assertThat(service.iceServers().toString()).isEqualTo(IceServersService.DEFAULT_ICE_SERVERS);
	}

	@Test
	void listaConfiguradaEDevolvidaComoVeio() {
		String json = "[{\"urls\":\"stun:stun.exemplo.com:80\"},"
				+ "{\"urls\":\"turn:turn.exemplo.com:443\",\"username\":\"u\",\"credential\":\"c\"}]";

		IceServersService service = new IceServersService(objectMapper, json);

		assertThat(service.iceServers()).hasSize(2);
		assertThat(service.iceServers().get(1).get("credential").asText()).isEqualTo("c");
	}

	@Test
	void configuracaoInvalidaImpedeASubida() {
		assertThatThrownBy(() -> new IceServersService(objectMapper, "nao e json"))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new IceServersService(objectMapper, "{\"urls\":\"stun:x\"}"))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new IceServersService(objectMapper, "[]"))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new IceServersService(objectMapper, "[{\"username\":\"u\"}]"))
				.isInstanceOf(IllegalStateException.class);
	}

}
