package com.lovemanagement.service;

import com.lovemanagement.domain.AppRole;
import com.lovemanagement.domain.FunctionPermission;
import com.lovemanagement.domain.Member;
import com.lovemanagement.domain.MemberFunctionPermission;
import com.lovemanagement.domain.UserAccount;
import com.lovemanagement.repo.AppRoleRepository;
import com.lovemanagement.repo.FunctionPermissionRepository;
import com.lovemanagement.repo.MemberFunctionPermissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 个人权限：成员单独设置功能权限。
 * 生效规则：若该成员有个人权限记录，则仅使用个人集；否则跟随其角色权限。
 */
@Service
public class PersonalPermissionService {

    private final MemberFunctionPermissionRepository mfpRepository;
    private final FunctionPermissionRepository permissionRepository;
    private final AppRoleRepository roleRepository;

    public PersonalPermissionService(MemberFunctionPermissionRepository mfpRepository,
                                     FunctionPermissionRepository permissionRepository,
                                     AppRoleRepository roleRepository) {
        this.mfpRepository = mfpRepository;
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
    }

    /** 获取成员已配置的个人权限 ID 列表（空列表 = 未配置，跟随角色）。 */
    public List<Long> getPermissionIds(Long memberId) {
        return mfpRepository.findByMemberId(memberId).stream()
                .map(MemberFunctionPermission::getPermissionId)
                .collect(Collectors.toList());
    }

    /** 保存成员个人权限（传空列表 = 清除个人权限，回到跟随角色）。 */
    @Transactional
    public void save(Long memberId, List<Long> permissionIds) {
        mfpRepository.deleteByMemberId(memberId);
        if (permissionIds != null) {
            for (Long pid : permissionIds) {
                mfpRepository.save(new MemberFunctionPermission(memberId, pid));
            }
        }
    }

    /** 解析成员生效的功能权限集合。 */
    public Set<String> resolveEffectivePermissions(Member member, UserAccount account) {
        // 1. 检查是否有个人权限
        List<MemberFunctionPermission> personal = mfpRepository.findByMemberId(member.getId());
        if (!personal.isEmpty()) {
            return personal.stream()
                    .map(p -> permissionRepository.findById(p.getPermissionId())
                            .map(FunctionPermission::getCode)
                            .orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
        }
        // 2. 未配置个人权限 → 跟随角色
        String roleCode = account.getAccountRole().name();
        return roleRepository.findByCode(roleCode)
                .map(role -> role.getPermissions().stream()
                        .map(FunctionPermission::getCode)
                        .collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    /** 判断成员是否有某功能权限。 */
    public boolean hasPermission(Member member, UserAccount account, String permissionCode) {
        return resolveEffectivePermissions(member, account).contains(permissionCode);
    }

    public List<FunctionPermission> allPermissions() {
        return permissionRepository.findAll();
    }
}
