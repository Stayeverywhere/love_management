package com.lovemanagement.service;

import com.lovemanagement.domain.AppRole;
import com.lovemanagement.domain.DataScope;
import com.lovemanagement.domain.FunctionPermission;
import com.lovemanagement.domain.PolicyException;
import com.lovemanagement.repo.AppRoleRepository;
import com.lovemanagement.repo.FunctionPermissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class RoleService {

    private final AppRoleRepository appRoleRepository;
    private final FunctionPermissionRepository permissionRepository;

    public RoleService(AppRoleRepository appRoleRepository, FunctionPermissionRepository permissionRepository) {
        this.appRoleRepository = appRoleRepository;
        this.permissionRepository = permissionRepository;
    }

    public List<AppRole> list() {
        return appRoleRepository.findAllByOrderByIdAsc();
    }

    public AppRole get(Long id) {
        return appRoleRepository.findById(id).orElseThrow(() -> new PolicyException("角色不存在"));
    }

    public List<FunctionPermission> allPermissions() {
        return permissionRepository.findAllByOrderByIdAsc();
    }

    @Transactional
    public AppRole save(Long id, String name, String description, String dataScope) {
        String normalized = RoleNamePolicy.normalize(name);
        String desc = description == null ? "" : description.trim();
        if (desc.length() > 100) {
            throw new PolicyException("角色说明不能超过 100 个字符");
        }
        boolean duplicated = id == null
                ? appRoleRepository.existsByName(normalized)
                : appRoleRepository.existsByNameAndIdNot(normalized, id);
        if (duplicated) {
            throw new PolicyException("角色名称已存在");
        }
        AppRole role = id == null ? new AppRole() : get(id);
        role.setName(normalized);
        role.setDescription(desc);
        role.setDataScope(normalizeDataScope(dataScope));
        return appRoleRepository.save(role);
    }

    @Transactional
    private String normalizeDataScope(String dataScope) {
        if (dataScope == null || dataScope.isBlank()) {
            return DataScope.ALL.name();
        }
        try {
            return DataScope.valueOf(dataScope.trim().toUpperCase()).name();
        } catch (IllegalArgumentException ex) {
            throw new PolicyException("数据权限范围不合法");
        }
    }

    public AppRole savePermissions(Long roleId, Collection<String> codes) {
        AppRole role = get(roleId);
        Set<String> requested = new LinkedHashSet<>();
        if (codes != null) {
            for (String code : codes) {
                if (code != null && !code.isBlank()) {
                    requested.add(code.trim());
                }
            }
        }
        List<FunctionPermission> selected = requested.isEmpty()
                ? List.of()
                : permissionRepository.findByCodeIn(requested);
        if (selected.size() != requested.size()) {
            throw new PolicyException("包含未知的功能权限");
        }
        role.getPermissions().clear();
        role.getPermissions().addAll(selected);
        return appRoleRepository.save(role);
    }
}
