package br.edu.webchat.user.service;

import br.edu.webchat.shared.exception.ConflictException;
import br.edu.webchat.shared.exception.NotFoundException;
import br.edu.webchat.user.dto.CreateUserRequest;
import br.edu.webchat.user.dto.UpdateUserRequest;
import br.edu.webchat.user.dto.UserResponse;
import br.edu.webchat.user.entity.User;
import br.edu.webchat.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public UserResponse create(CreateUserRequest request) {
		String email = normalizeEmail(request.email());

		if (userRepository.existsByEmail(email)) {
			throw new ConflictException("E-mail ja cadastrado: " + email);
		}

		User user = new User(request.name().trim(), email, passwordEncoder.encode(request.password()));

		return UserResponse.from(userRepository.save(user));
	}

	@Transactional(readOnly = true)
	public List<UserResponse> findAll() {
		return userRepository.findAll().stream()
				.map(UserResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public UserResponse findByEmail(String email) {
		return UserResponse.from(userRepository.findByEmail(normalizeEmail(email))
				.orElseThrow(() -> new NotFoundException("Usuario nao encontrado: " + email)));
	}

	@Transactional(readOnly = true)
	public UserResponse findById(Long id) {
		return UserResponse.from(findEntityById(id));
	}

	@Transactional
	public UserResponse update(Long id, UpdateUserRequest request) {
		User user = findEntityById(id);
		user.setName(request.name().trim());
		return UserResponse.from(userRepository.saveAndFlush(user));
	}

	private User findEntityById(Long id) {
		return userRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Usuario nao encontrado: " + id));
	}

	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase();
	}

}
