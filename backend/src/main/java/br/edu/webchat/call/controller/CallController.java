package br.edu.webchat.call.controller;

import br.edu.webchat.call.service.IceServersService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Configuracao de rede das chamadas. A sinalizacao em si fica no CallWebSocketController. */
@RestController
@RequestMapping("/api/v1/calls")
public class CallController {

	private final IceServersService iceServersService;

	public CallController(IceServersService iceServersService) {
		this.iceServersService = iceServersService;
	}

	/** Exige login (regra geral do SecurityConfig): as credenciais do TURN nao sao publicas. */
	@GetMapping("/ice-servers")
	public JsonNode iceServers() {
		return iceServersService.iceServers();
	}

}
