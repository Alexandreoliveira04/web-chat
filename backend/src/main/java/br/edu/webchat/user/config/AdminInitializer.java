package br.edu.webchat.user.config;

import br.edu.webchat.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;


@Component
public class AdminInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

	private final UserService userService;
	private final String name;
	private final String email;
	private final String password;

	public AdminInitializer(UserService userService,
			@Value("${app.admin.name:Administrador}") String name,
			@Value("${app.admin.email:}") String email,
			@Value("${app.admin.password:}") String password) {
		this.userService = userService;
		this.name = name;
		this.email = email;
		this.password = password;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (email.isBlank()) {
			log.debug("app.admin.email nao configurado: administrador inicial nao sera criado");
			return;
		}

		userService.ensureAdmin(name, email, password);
		log.info("Administrador inicial garantido: {}", email);
	}

}
