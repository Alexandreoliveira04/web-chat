package br.edu.webchat.auth.controller;

import br.edu.webchat.auth.service.AuthService;
import br.edu.webchat.shared.exception.ConflictException;
import br.edu.webchat.user.dto.CreateUserRequest;
import br.edu.webchat.user.dto.UserResponse;
import br.edu.webchat.user.entity.Role;
import br.edu.webchat.user.entity.UserStatus;
import br.edu.webchat.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
// addFilters = false: estes testes verificam o controller, nao a seguranca.
// A cadeia de seguranca real e coberta por SecurityIntegrationTest.
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private UserService userService;

	private static final UserResponse ALEXANDRE = new UserResponse(
			1L, "Alexandre Oliveira", "alexandre@email.com", UserStatus.OFFLINE, Role.USER,
			Instant.parse("2026-09-08T22:00:00Z"), Instant.parse("2026-09-08T22:00:00Z"));

	@Test
	void registerDeveRetornar201ComLocation() throws Exception {
		when(userService.create(any(CreateUserRequest.class))).thenReturn(ALEXANDRE);

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateUserRequest(
								"Alexandre Oliveira", "alexandre@email.com", "123456"))))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "http://localhost/api/v1/users/1"))
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.status").value("OFFLINE"))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.createdAt").value("2026-09-08T22:00:00Z"));
	}

	@Test
	void registerComDadosInvalidosDeveRetornar400ComOsCampos() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"A","email":"nao-e-email","password":"123"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.fields.name").exists())
				.andExpect(jsonPath("$.fields.email").exists())
				.andExpect(jsonPath("$.fields.password").exists());
	}

	@Test
	void registerComEmailDuplicadoDeveRetornar409() throws Exception {
		when(userService.create(any(CreateUserRequest.class)))
				.thenThrow(new ConflictException("E-mail ja cadastrado: alexandre@email.com"));

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateUserRequest(
								"Alexandre Oliveira", "alexandre@email.com", "123456"))))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
	}

}
