package com.mdplatform.repository;

import com.mdplatform.model.SysRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<SysRole, Long> {

    Optional<SysRole> findByRoleCode(String roleCode);

    Optional<SysRole> findByRoleName(String roleName);
}
