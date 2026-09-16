package br.edu.webchat.auth.service;

import br.edu.webchat.auth.dto.LoginRequest;
import br.edu.webchat.auth.dto.LoginResponse;
import br.edu.webchat.auth.jwt.JwtService;
import br.edu.webchat.shared.exception.UnauthorizedException;
import br.edu.webchat.user.entity.User;
import br.edu.webchat.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {

	private static final String CREDENCIAIS_INVALIDAS = "E-mail ou senha invalidos";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
			JwtService jwtService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	@Transactional(readOnly = true)
	public LoginResponse login(LoginRequest request) {
		String email = request.email().trim().toLowerCase();
		Optional<User> found = userRepository.findByEmail(email);

		if (found.isEmpty() || !passwordEncoder.matches(request.password(), found.get().getPassword())) {
			throw new UnauthorizedException(CREDENCIAIS_INVALIDAS);
		}

		String token = jwtService.generateToken(found.get().getEmail());

		return LoginResponse.bearer(token, jwtService.getExpirationSeconds());
	}

}
