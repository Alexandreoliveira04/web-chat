package br.edu.webchat.user.dto;

import br.edu.webchat.user.entity.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(

		@NotNull
		Role role

) {
}
