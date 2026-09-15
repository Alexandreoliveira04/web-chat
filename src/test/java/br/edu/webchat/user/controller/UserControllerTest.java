package br.edu.webchat.user.controller;

import br.edu.webchat.shared.exception.ForbiddenException;
import br.edu.webchat.shared.exception.NotFoundException;
import br.edu.webchat.user.dto.UpdateUserRequest;
import br.edu.webchat.user.dto.UserResponse;
import br.edu.webchat.user.entity.Role;
import br.edu.webchat.user.entity.UserStatus;
import br.edu.webchat.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
// addFilters = false: estes testes verificam o controller, nao a seguranca.
// A cadeia de seguranca real (incluindo os papeis) e coberta por SecurityIntegrationTest.
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	private static final UserResponse ALEXANDRE = new UserResponse(
			1L, "Alexandre Oliveira", "alexandre@email.com", UserStatus.OFFLINE, Role.USER,
			Instant.parse("2026-09-08T22:00:00Z"), Instant.parse("2026-09-08T22:00:00Z"));

	private static final Authentication LOGADO =
			new UsernamePasswordAuthenticationToken("alexandre@email.com", null, List.of());

	@Test
	void respostaNuncaDeveConterSenha() throws Exception {
		when(userService.findById(1L)).thenReturn(ALEXANDRE);

		String body = mockMvc.perform(get("/api/v1/users/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.role").value("USER"))
				.andReturn().getResponse().getContentAsString();

		org.assertj.core.api.Assertions.assertThat(body).doesNotContain("password");
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
	void putMeDeveAtualizarOUsuarioAutenticado() throws Exception {
		UserResponse atualizado = new UserResponse(1L, "Alexandre Oliveira Silva",
				"alexandre@email.com", UserStatus.OFFLINE, Role.USER, ALEXANDRE.createdAt(), Instant.now());
		when(userService.updateMe(eq("alexandre@email.com"), any(UpdateUserRequest.class))).thenReturn(atualizado);

		mockMvc.perform(put("/api/v1/users/me")
						.principal(LOGADO)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Alexandre Oliveira Silva"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Alexandre Oliveira Silva"));
	}

	@Test
	void putMeComNomeInvalidoDeveRetornar400() throws Exception {
		mockMvc.perform(put("/api/v1/users/me")
						.principal(LOGADO)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":" "}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.name").exists());
	}

	@Test
	void putPorIdDeveAtualizarONome() throws Exception {
		UserResponse atualizado = new UserResponse(1L, "Alexandre Oliveira Silva",
				"alexandre@email.com", UserStatus.OFFLINE, Role.USER, ALEXANDRE.createdAt(), Instant.now());
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
	void patchRoleDeveAlterarOPapel() throws Exception {
		UserResponse promovido = new UserResponse(1L, "Alexandre Oliveira", "alexandre@email.com",
				UserStatus.OFFLINE, Role.ADMIN, ALEXANDRE.createdAt(), Instant.now());
		when(userService.changeRole(1L, Role.ADMIN, "admin@email.com")).thenReturn(promovido);

		mockMvc.perform(patch("/api/v1/users/1/role")
						.principal(new UsernamePasswordAuthenticationToken("admin@email.com", null, List.of()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"role":"ADMIN"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("ADMIN"));
	}

	@Test
	void patchRoleSemPapelDeveRetornar400() throws Exception {
		mockMvc.perform(patch("/api/v1/users/1/role")
						.principal(LOGADO)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.role").exists());
	}

	@Test
	void patchRoleNoProprioUsuarioDeveRetornar403() throws Exception {
		when(userService.changeRole(1L, Role.USER, "alexandre@email.com"))
				.thenThrow(new ForbiddenException("Nao e permitido alterar o proprio papel"));

		mockMvc.perform(patch("/api/v1/users/1/role")
						.principal(LOGADO)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"role":"USER"}
								"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
	}

}
