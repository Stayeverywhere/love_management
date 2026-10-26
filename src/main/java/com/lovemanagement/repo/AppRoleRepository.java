package com.lovemanagement.repo;

import com.lovemanagement.domain.AppRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppRoleRepository extends JpaRepository<AppRole, Long> {

    List<AppRole> findAllByOrderByIdAsc();

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    java.util.Optional<com.lovemanagement.domain.AppRole> findByCode(String code);
}
