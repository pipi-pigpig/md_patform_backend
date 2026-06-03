package com.mdplatform.management.repository;

import com.mdplatform.management.model.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<SysUser, Long> {

    Optional<SysUser> findByUsername(String username);

    Optional<SysUser> findByEmail(String email);

    List<SysUser> findByOrganization(String organization);

    List<SysUser> findByRoleId(Long roleId);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}