package com.lovemanagement.repo;

import com.lovemanagement.domain.MemberFunctionPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberFunctionPermissionRepository extends JpaRepository<MemberFunctionPermission, Long> {

    List<MemberFunctionPermission> findByMemberId(Long memberId);

    void deleteByMemberId(Long memberId);

    boolean existsByMemberIdAndPermissionId(Long memberId, Long permissionId);
}
