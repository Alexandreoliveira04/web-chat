package br.edu.webchat.call.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Servidores STUN/TURN que o navegador usa para conectar o audio das chamadas.
 *
 * <p>O STUN so descobre o endereco publico de cada aparelho; quando a conexao direta e
 * bloqueada (4G, redes com firewall), o audio precisa passar por um servidor TURN. A lista
 * vem da variavel ICE_SERVERS, no formato de iceServers do WebRTC, porque o TURN tem
 * credenciais e o repositorio e publico. Sem a variavel, fica so o STUN publico do Google.
 */
@Service
public class IceServersService {

	static final String DEFAULT_ICE_SERVERS = "[{\"urls\":\"stun:stun.l.google.com:19302\"}]";

	private final JsonNode iceServers;

	public IceServersService(ObjectMapper objectMapper, @Value("${app.webrtc.ice-servers:}") String json) {
		String value = json == null || json.isBlank() ? DEFAULT_ICE_SERVERS : json;

		JsonNode parsed;
		try {
			parsed = objectMapper.readTree(value);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("ICE_SERVERS nao e um JSON valido", ex);
		}

		if (parsed == null || !parsed.isArray() || parsed.isEmpty()) {
			throw new IllegalStateException("ICE_SERVERS precisa ser uma lista JSON de servidores STUN/TURN");
		}
		for (JsonNode server : parsed) {
			if (!server.hasNonNull("urls")) {
				throw new IllegalStateException("Todo servidor em ICE_SERVERS precisa do campo urls");
			}
		}

		this.iceServers = parsed;
	}

	public JsonNode iceServers() {
		return iceServers;
	}

}
