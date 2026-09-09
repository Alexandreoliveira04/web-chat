package br.edu.webchat.user.controller;

import br.edu.webchat.shared.exception.ConflictException;
import br.edu.webchat.shared.exception.NotFoundException;
import br.edu.webchat.user.dto.CreateUserRequest;
import br.edu.webchat.user.dto.UpdateUserRequest;
import br.edu.webchat.user.dto.UserResponse;
import br.edu.webchat.user.entity.UserStatus;
import br.edu.webchat.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private UserService userService;

	private static final UserResponse ALEXANDRE = new UserResponse(
			1L, "Alexandre Oliveira", "alexandre@email.com", UserStatus.OFFLINE,
			Instant.parse("2026-09-08T22:00:00Z"), Instant.parse("2026-09-08T22:00:00Z"));

	@Test
	void postDeveRetornar201ComLocation() throws Exception {
		when(userService.create(any(CreateUserRequest.class))).thenReturn(ALEXANDRE);

		mockMvc.perform(post("/api/v1/users")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateUserRequest(
								"Alexandre Oliveira", "alexandre@email.com", "123456"))))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "http://localhost/api/v1/users/1"))
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.status").value("OFFLINE"))
				.andExpect(jsonPath("$.createdAt").value("2026-09-08T22:00:00Z"));
	}

	@Test
	void respostaNuncaDeveConterSenha() throws Exception {
		when(userService.findById(1L)).thenReturn(ALEXANDRE);

		String body = mockMvc.perform(get("/api/v1/users/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.password").doesNotExist())
				.andReturn().getResponse().getContentAsString();

		org.assertj.core.api.Assertions.assertThat(body).doesNotContain("password");
	}

	@Test
	void postComDadosInvalidosDeveRetornar400ComOsCampos() throws Exception {
		mockMvc.perform(post("/api/v1/users")
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
	void postComEmailDuplicadoDeveRetornar409() throws Exception {
		when(userService.create(any(CreateUserRequest.class)))
				.thenThrow(new ConflictException("E-mail ja cadastrado: alexandre@email.com"));

		mockMvc.perform(post("/api/v1/users")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateUserRequest(
								"Alexandre Oliveira", "alexandre@email.com", "123456"))))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
	}

	@Test
	void getDeveListarUsuarios() throws Exception {
		when(userService.findAll()).thenReturn(List.of(ALEXANDRE));

		mockMvc.perform(get("/api/v1/users"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].email").value("alexandre@email.com"));
	}

	@Test
	void getPorIdInexistenteDeveRetornar404() throws Exception {
		when(userService.findById(99L)).thenThrow(new NotFoundException("Usuario nao encontrado: 99"));

		mockMvc.perform(get("/api/v1/users/99"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.path").value("/api/v1/users/99"));
	}

	@Test
	void putDeveAtualizarONome() throws Exception {
		UserResponse atualizado = new UserResponse(1L, "Alexandre Oliveira Silva",
				"alexandre@email.com", UserStatus.OFFLINE, ALEXANDRE.createdAt(), Instant.now());
		when(userService.update(eq(1L), any(UpdateUserRequest.class))).thenReturn(atualizado);

		mockMvc.perform(put("/api/v1/users/1")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Alexandre Oliveira Silva"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Alexandre Oliveira Silva"));
	}

	@Test
	void putComNomeInvalidoDeveRetornar400() throws Exception {
		mockMvc.perform(put("/api/v1/users/1")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":" "}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.name").exists());
	}

}
