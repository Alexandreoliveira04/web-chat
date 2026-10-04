package br.edu.webchat.user.service;

import br.edu.webchat.shared.exception.ConflictException;
import br.edu.webchat.shared.exception.ForbiddenException;
import br.edu.webchat.shared.exception.NotFoundException;
import br.edu.webchat.user.dto.CreateUserRequest;
import br.edu.webchat.user.dto.UpdateUserRequest;
import br.edu.webchat.user.dto.UserResponse;
import br.edu.webchat.user.entity.Role;
import br.edu.webchat.user.entity.User;
import br.edu.webchat.user.entity.UserStatus;
import br.edu.webchat.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.List;

@Service
public class UserService {

	private static final int MIN_PASSWORD_LENGTH = 6;
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
		return UserResponse.from(findEntityByEmail(email));
	}

	@Transactional(readOnly = true)
	public UserResponse findById(Long id) {
		return UserResponse.from(findEntityById(id));
	}

	@Transactional
	public UserResponse updateMe(String authenticatedEmail, UpdateUserRequest request) {
		return rename(findEntityByEmail(authenticatedEmail), request);
	}

	@Transactional
	public UserResponse update(Long id, UpdateUserRequest request) {
		return rename(findEntityById(id), request);
	}

	@Transactional
	public UserResponse changeRole(Long id, Role role, String authenticatedEmail) {
		User user = findEntityById(id);

		if (user.getEmail().equals(normalizeEmail(authenticatedEmail))) {
			throw new ForbiddenException("Nao e permitido alterar o proprio papel");
		}

		user.setRole(role);
		return UserResponse.from(userRepository.saveAndFlush(user));
	}

	@Transactional
	public UserResponse ensureAdmin(String name, String email, String rawPassword) {
		String normalized = normalizeEmail(email);

		User admin = userRepository.findByEmail(normalized)
				.map(existing -> {
					existing.setRole(Role.ADMIN);
					return existing;
				})
				.orElseGet(() -> {
					if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
						throw new IllegalStateException("A senha do administrador inicial precisa ter no minimo "
								+ MIN_PASSWORD_LENGTH + " caracteres");
					}
					return new User(name.trim(), normalized, passwordEncoder.encode(rawPassword), Role.ADMIN);
				});

		return UserResponse.from(userRepository.saveAndFlush(admin));
	}

	@Transactional
	public Long changeStatus(String email, UserStatus status) {
		User user = findEntityByEmail(email);
		userRepository.updateStatus(user.getId(), status);
		return user.getId();
	}

	@Transactional
	public int markAllOffline() {
		return userRepository.updateAllStatuses(UserStatus.OFFLINE);
	}

	@Transactional
	public UserResponse uploadAvatar(String authenticatedEmail, MultipartFile file) {
		User user = findEntityByEmail(authenticatedEmail);

		try {
			Path uploadDir = Paths.get("uploads", "avatars");
			if (!Files.exists(uploadDir)) {
				Files.createDirectories(uploadDir);
			}

			String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "avatar.png");
			String extension = originalFilename.contains(".") ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".png";
			String newFilename = UUID.randomUUID().toString() + extension;
			Path targetLocation = uploadDir.resolve(newFilename);
			
			Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
			
			// Constrói a URL que será servida pelo WebConfig (ex: /uploads/avatars/uuid.png)
			String avatarUrl = "/uploads/avatars/" + newFilename;
			user.setAvatarUrl(avatarUrl);
			
			return UserResponse.from(userRepository.saveAndFlush(user));
		} catch (IOException ex) {
			throw new RuntimeException("Falha ao salvar a imagem do avatar", ex);
		}
	}

	private UserResponse rename(User user, UpdateUserRequest request) {
		user.setName(request.name().trim());
		return UserResponse.from(userRepository.saveAndFlush(user));
	}

	private User findEntityById(Long id) {
		return userRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Usuario nao encontrado: " + id));
	}

	private User findEntityByEmail(String email) {
		return userRepository.findByEmail(normalizeEmail(email))
				.orElseThrow(() -> new NotFoundException("Usuario nao encontrado: " + email));
	}

	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase();
	}

}
