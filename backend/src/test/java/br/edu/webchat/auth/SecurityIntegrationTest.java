package br.edu.webchat.auth;

import br.edu.webchat.auth.jwt.JwtService;
import br.edu.webchat.user.dto.CreateUserRequest;
import br.edu.webchat.user.entity.Role;
import br.edu.webchat.user.repository.UserRepository;
import br.edu.webchat.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercita a cadeia de seguranca real: quem passa sem token, quem exige JWT e quem exige ADMIN. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

	private static final String EMAIL = "alexandre@email.com";
	private static final String SENHA = "123456";
	private static final String ADMIN_EMAIL = "admin@email.com";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtService jwtService;

	private Long userId;
	private Long adminId;

	@BeforeEach
	void prepararUsuarios() {
		userRepository.deleteAll();
		userId = userService.create(new CreateUserRequest("Alexandre Oliveira", EMAIL, SENHA)).id();
		adminId = userService.ensureAdmin("Administrador", ADMIN_EMAIL, "admin123").id();
	}

	// ---------- endpoints publicos ----------

	@Test
	void healthDeveSerPublico() throws Exception {
		mockMvc.perform(get("/api/v1/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void registerDeveSerPublico() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new CreateUserRequest("Joao Silva", "joao@email.com", "senha123"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.role").value("USER"));
	}

	@Test
	void registerDeveIgnorarPapelEnviadoNoCorpo() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Joao Silva","email":"joao@email.com","password":"senha123","role":"ADMIN"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.role").value("USER"));
	}

	@Test
	void antigaRotaDeCadastroNaoDeveMaisSerPublica() throws Exception {
		mockMvc.perform(post("/api/v1/users")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new CreateUserRequest("Joao Silva", "joao@email.com", "senha123"))))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void loginDeveSerPublicoEDevolverToken() throws Exception {
		mockMvc.perform(login(EMAIL, SENHA))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresIn").value(3600))
				.andExpect(jsonPath("$.password").doesNotExist());
	}

	// ---------- login ----------

	@Test
	void loginComEmailInexistenteDeveRetornar401() throws Exception {
		mockMvc.perform(login("ninguem@email.com", SENHA))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void loginComSenhaIncorretaDeveRetornar401() throws Exception {
		mockMvc.perform(login(EMAIL, "senha-errada"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void loginComEmailInvalidoDeveRetornar400() throws Exception {
		mockMvc.perform(login("nao-e-email", SENHA))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.email").exists());
	}

	@Test
	void loginComSenhaVaziaDeveRetornar400() throws Exception {
		mockMvc.perform(login(EMAIL, ""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.password").exists());
	}

	// ---------- /users/me ----------

	@Test
	void meSemTokenDeveRetornar401() throws Exception {
		mockMvc.perform(get("/api/v1/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.path").value("/api/v1/users/me"));
	}

	@Test
	void meComTokenValidoDeveRetornarOProprioUsuario() throws Exception {
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(userId))
				.andExpect(jsonPath("$.email").value(EMAIL))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.password").doesNotExist());
	}

	@Test
	void meComTokenInvalidoDeveRetornar401() throws Exception {
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer token-invalido"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void meComTokenExpiradoDeveRetornar401() throws Exception {
		String expirado = new JwtService("segredo-de-teste-com-tamanho-suficiente-para-hs256", -60)
				.generateToken(EMAIL);

		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + expirado))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void meSemPrefixoBearerDeveRetornar401() throws Exception {
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", jwtService.generateToken(EMAIL)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void atualizarMeSemTokenDeveRetornar401() throws Exception {
		mockMvc.perform(put("/api/v1/users/me")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Outro Nome\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void usuarioComumDeveAtualizarOProprioPerfilPorMe() throws Exception {
		mockMvc.perform(put("/api/v1/users/me")
						.header("Authorization", bearer(EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Alexandre Oliveira Silva\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(userId))
				.andExpect(jsonPath("$.name").value("Alexandre Oliveira Silva"));
	}

	// ---------- demais endpoints de USER ----------

	@Test
	void listarUsuariosSemTokenDeveRetornar401() throws Exception {
		mockMvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
	}

	@Test
	void buscarUsuarioSemTokenDeveRetornar401() throws Exception {
		mockMvc.perform(get("/api/v1/users/" + userId)).andExpect(status().isUnauthorized());
	}

	@Test
	void atualizarUsuarioSemTokenDeveRetornar401() throws Exception {
		mockMvc.perform(put("/api/v1/users/" + userId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Outro Nome\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void endpointsDeLeituraDevemFuncionarComToken() throws Exception {
		String token = bearer(EMAIL);

		mockMvc.perform(get("/api/v1/users").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));

		mockMvc.perform(get("/api/v1/users/" + adminId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("ADMIN"));
	}

	@Test
	void tokenDeUsuarioRemovidoDeveRetornar401() throws Exception {
		String token = bearer(EMAIL);
		userRepository.deleteAll();

		mockMvc.perform(get("/api/v1/users/me").header("Authorization", token))
				.andExpect(status().isUnauthorized());
	}

	// ---------- papeis ----------

	@Test
	void usuarioComumNaoDeveAtualizarOutroUsuarioPorId() throws Exception {
		mockMvc.perform(put("/api/v1/users/" + adminId)
						.header("Authorization", bearer(EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Invadido\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));

		assertThat(userRepository.findById(adminId).orElseThrow().getName()).isEqualTo("Administrador");
	}

	@Test
	void usuarioComumNaoDeveAtualizarNemASiMesmoPorId() throws Exception {
		mockMvc.perform(put("/api/v1/users/" + userId)
						.header("Authorization", bearer(EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Outro Nome\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminDeveAtualizarQualquerUsuarioPorId() throws Exception {
		mockMvc.perform(put("/api/v1/users/" + userId)
						.header("Authorization", bearer(ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Nome Corrigido\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Nome Corrigido"));
	}

	@Test
	void usuarioComumNaoDeveAlterarPapeis() throws Exception {
		mockMvc.perform(alterarPapel(userId, "ADMIN", bearer(EMAIL)))
				.andExpect(status().isForbidden());

		assertThat(userRepository.findById(userId).orElseThrow().getRole()).isEqualTo(Role.USER);
	}

	@Test
	void adminNaoDeveAlterarOProprioPapel() throws Exception {
		mockMvc.perform(alterarPapel(adminId, "USER", bearer(ADMIN_EMAIL)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("Nao e permitido alterar o proprio papel"));
	}

	@Test
	void promocaoDeveValerImediatamenteComOMesmoToken() throws Exception {
		String tokenDoUsuario = bearer(EMAIL);

		mockMvc.perform(alterarPapel(userId, "ADMIN", bearer(ADMIN_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("ADMIN"));

		// o papel e lido do banco a cada requisicao, nao do token
		mockMvc.perform(put("/api/v1/users/" + adminId)
						.header("Authorization", tokenDoUsuario)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Admin Renomeado\"}"))
				.andExpect(status().isOk());
	}

	private String bearer(String email) {
		return "Bearer " + jwtService.generateToken(email);
	}

	private MockHttpServletRequestBuilder alterarPapel(Long id, String role, String authorization) {
		return patch("/api/v1/users/" + id + "/role")
				.header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"role\":\"" + role + "\"}");
	}

	private MockHttpServletRequestBuilder login(String email, String senha) {
		return post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + senha + "\"}");
	}

}
