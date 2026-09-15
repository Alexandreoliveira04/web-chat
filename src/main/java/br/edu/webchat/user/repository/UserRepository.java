package br.edu.webchat.user.repository;

import br.edu.webchat.user.entity.User;
import br.edu.webchat.user.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	@Modifying
	@Query("update User u set u.status = :status where u.id = :id")
	int updateStatus(@Param("id") Long id, @Param("status") UserStatus status);

	@Modifying
	@Query("update User u set u.status = :status where u.status <> :status")
	int updateAllStatuses(@Param("status") UserStatus status);

}
