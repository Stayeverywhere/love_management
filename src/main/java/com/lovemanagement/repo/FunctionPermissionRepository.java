package com.lovemanagement.repo;

import com.lovemanagement.domain.FunctionPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FunctionPermissionRepository extends JpaRepository<FunctionPermission, Long> {

    Optional<FunctionPermission> findByCode(String code);

    List<FunctionPermission> findByCodeIn(Collection<String> codes);

    List<FunctionPermission> findAllByOrderByIdAsc();
}
