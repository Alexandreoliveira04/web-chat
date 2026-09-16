package br.edu.webchat.user.controller;

import br.edu.webchat.user.dto.UpdateRoleRequest;
import br.edu.webchat.user.dto.UpdateUserRequest;
import br.edu.webchat.user.dto.UserResponse;
import br.edu.webchat.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public List<UserResponse> findAll() {
		return userService.findAll();
	}

	@GetMapping("/me")
	public UserResponse me(Authentication authentication) {
		return userService.findByEmail(authentication.getName());
	}

	@PutMapping("/me")
	public UserResponse updateMe(Authentication authentication, @Valid @RequestBody UpdateUserRequest request) {
		return userService.updateMe(authentication.getName(), request);
	}

	@GetMapping("/{id}")
	public UserResponse findById(@PathVariable Long id) {
		return userService.findById(id);
	}

	@PutMapping("/{id}")
	public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
		return userService.update(id, request);
	}

	@PatchMapping("/{id}/role")
	public UserResponse changeRole(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request,
			Authentication authentication) {
		return userService.changeRole(id, request.role(), authentication.getName());
	}

}
