package br.edu.webchat.call.controller;

import br.edu.webchat.call.service.IceServersService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CallController.class)
// addFilters = false: este teste verifica o controller; a exigencia de login e da regra
// anyRequest().authenticated() do SecurityConfig.
@AutoConfigureMockMvc(addFilters = false)
class CallControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private IceServersService iceServersService;

	@Test
	void devolveAListaDeServidoresStunETurn() throws Exception {
		when(iceServersService.iceServers()).thenReturn(new ObjectMapper().readTree(
				"[{\"urls\":\"stun:stun.exemplo.com:80\"},"
						+ "{\"urls\":\"turn:turn.exemplo.com:443\",\"username\":\"u\",\"credential\":\"c\"}]"));

		mockMvc.perform(get("/api/v1/calls/ice-servers"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[1].urls").value("turn:turn.exemplo.com:443"))
				.andExpect(jsonPath("$[1].username").value("u"));
	}

}
