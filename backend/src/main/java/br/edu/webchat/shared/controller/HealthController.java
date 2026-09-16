package br.edu.webchat.shared.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Verificacao simples de disponibilidade da API. */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

	@GetMapping
	public Map<String, String> health() {
		return Map.of("status", "UP");
	}

}
