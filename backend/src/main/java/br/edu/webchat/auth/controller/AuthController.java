package br.edu.webchat.auth.controller;

import br.edu.webchat.auth.dto.LoginRequest;
import br.edu.webchat.auth.dto.LoginResponse;
import br.edu.webchat.auth.service.AuthService;
import br.edu.webchat.user.dto.CreateUserRequest;
import br.edu.webchat.user.dto.UserResponse;
import br.edu.webchat.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;
	private final UserService userService;

	public AuthController(AuthService authService, UserService userService) {
		this.authService = authService;
		this.userService = userService;
	}

	@PostMapping("/register")
	public ResponseEntity<UserResponse> register(@Valid @RequestBody CreateUserRequest request,
			UriComponentsBuilder uriBuilder) {
		UserResponse created = userService.create(request);

		return ResponseEntity
				.created(uriBuilder.path("/api/v1/users/{id}").buildAndExpand(created.id()).toUri())
				.body(created);
	}

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

}
