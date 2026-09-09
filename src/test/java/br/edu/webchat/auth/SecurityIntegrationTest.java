package br.edu.webchat.auth;

import br.edu.webchat.auth.jwt.JwtService;
import br.edu.webchat.user.dto.CreateUserRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercita a cadeia de seguranca real: quem passa sem token, quem exige JWT. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

	private static final String EMAIL = "alexandre@email.com";
	private static final String SENHA = "123456";

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

	@BeforeEach
	void prepararUsuario() {
		userRepository.deleteAll();
		userId = userService.create(new CreateUserRequest("Alexandre Oliveira", EMAIL, SENHA)).id();
	}

	// ---------- endpoints publicos ----------

	@Test
	void healthDeveSerPublico() throws Exception {
		mockMvc.perform(get("/api/v1/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void cadastroDeUsuarioDeveContinuarPublico() throws Exception {
		mockMvc.perform(post("/api/v1/users")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new CreateUserRequest("Joao Silva", "joao@email.com", "senha123"))))
				.andExpect(status().isCreated());
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
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + tokenValido()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(userId))
				.andExpect(jsonPath("$.email").value(EMAIL))
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
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", tokenValido()))
				.andExpect(status().isUnauthorized());
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
	void endpointsDeUserDevemFuncionarComToken() throws Exception {
		String token = "Bearer " + tokenValido();

		mockMvc.perform(get("/api/v1/users").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].email").value(EMAIL));

		mockMvc.perform(get("/api/v1/users/" + userId).header("Authorization", token))
				.andExpect(status().isOk());

		mockMvc.perform(put("/api/v1/users/" + userId)
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Alexandre Oliveira Silva\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Alexandre Oliveira Silva"));
	}

	@Test
	void tokenDeUsuarioRemovidoDeveRetornar401() throws Exception {
		String token = tokenValido();
		userRepository.deleteAll();

		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized());
	}

	private String tokenValido() {
		return jwtService.generateToken(EMAIL);
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
			String email, String senha) throws Exception {
		return post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + senha + "\"}");
	}

}
