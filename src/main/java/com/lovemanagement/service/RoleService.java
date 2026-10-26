package com.lovemanagement.service;

import com.lovemanagement.domain.AppRole;
import com.lovemanagement.domain.DataScope;
import com.lovemanagement.domain.FunctionPermission;
import com.lovemanagement.domain.PolicyException;
import com.lovemanagement.repo.AppRoleRepository;
import com.lovemanagement.repo.FunctionPermissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoleService {

    private final AppRoleRepository appRoleRepository;
    private final FunctionPermissionRepository permissionRepository;
    private final RoleNamePolicy roleNamePolicy;
    private final OperationLogService operationLogService;

    public RoleService(AppRoleRepository appRoleRepository,
                       FunctionPermissionRepository permissionRepository,
                       RoleNamePolicy roleNamePolicy,
                       OperationLogService operationLogService) {
        this.appRoleRepository = appRoleRepository;
        this.permissionRepository = permissionRepository;
        this.roleNamePolicy = roleNamePolicy;
        this.operationLogService = operationLogService;
    }

    public List<AppRole> list() {
        return appRoleRepository.findAll();
    }

    public AppRole get(Long id) {
        return appRoleRepository.findById(id)
                .orElseThrow(() -> new PolicyException("角色不存在"));
    }

    public List<FunctionPermission> allPermissions() {
        return permissionRepository.findAll();
    }

    @Transactional
    public AppRole save(Long id, String name, String description, String dataScope) {
        String normalizedName = roleNamePolicy.normalize(name);
        if (id != null) {
            AppRole role = get(id);
            if (role.getName().equals(normalizedName)) {
                role.setDescription(description);
                role.setDataScope(normalizeDataScope(dataScope));
                return appRoleRepository.save(role);
            }
            if (appRoleRepository.existsByNameAndIdNot(normalizedName, id)) {
                throw new PolicyException("角色名称已存在");
            }
            role.setName(normalizedName);
            role.setDescription(description);
            role.setDataScope(normalizeDataScope(dataScope));
            operationLogService.log("角色修改", "ROLE", id,
                    "名称改为「" + normalizedName + "」");
            return appRoleRepository.save(role);
        }
        if (appRoleRepository.existsByName(normalizedName)) {
            throw new PolicyException("角色名称已存在");
        }
        AppRole role = new AppRole();
        role.setName(normalizedName);
        role.setDescription(description);
        role.setDataScope(normalizeDataScope(dataScope));
        AppRole saved = appRoleRepository.save(role);
        operationLogService.log("角色创建", "ROLE", saved.getId(),
                "创建角色「" + normalizedName + "」");
        return saved;
    }

    /** 复制角色：生成"名称(副本)"并复制全部权限。 */
    @Transactional
    public AppRole copy(Long id) {
        AppRole source = get(id);
        String newName = source.getName() + "(副本)";
        int suffix = 2;
        while (appRoleRepository.existsByName(newName)) {
            newName = source.getName() + "(副本" + suffix + ")";
            suffix++;
        }
        AppRole copy = new AppRole();
        copy.setName(newName);
        copy.setDescription(source.getDescription());
        copy.setDataScope(source.getDataScope());
        copy.setCode(null); // 副本不绑定登录角色编码
        AppRole saved = appRoleRepository.save(copy);
        // 复制权限
        List<Long> permIds = source.getPermissions().stream()
                .map(FunctionPermission::getId)
                .collect(Collectors.toList());
        saved.getPermissions().addAll(permIds.stream()
                .map(permissionRepository::getReferenceById)
                .collect(Collectors.toList()));
        appRoleRepository.save(saved);
        operationLogService.log("角色复制", "ROLE", saved.getId(),
                "从角色「" + source.getName() + "」复制为「" + newName + "」");
        return saved;
    }

    /** 启用/禁用角色。禁用后该角色的账号无法登录。 */
    @Transactional
    public void setEnabled(Long id, boolean enabled) {
        AppRole role = get(id);
        if (role.getCode() != null && role.getCode().equals("ADMIN") && !enabled) {
            throw new PolicyException("系统管理员角色不可禁用");
        }
        role.setEnabled(enabled);
        appRoleRepository.save(role);
        operationLogService.log(enabled ? "角色启用" : "角色禁用", "ROLE", id,
                (enabled ? "启用" : "禁用") + "角色「" + role.getName() + "」");
    }

    @Transactional
    public void savePermissions(Long id, List<String> codes) {
        AppRole role = get(id);
        List<String> oldCodes = role.getPermissions().stream()
                .map(FunctionPermission::getCode)
                .collect(Collectors.toList());
        List<String> newCodes = codes == null ? List.of() : codes.stream().distinct().toList();
        role.getPermissions().clear();
        for (String code : newCodes) {
            FunctionPermission permission = permissionRepository.findByCode(code)
                    .orElseThrow(() -> new PolicyException("功能权限不存在：" + code));
            role.getPermissions().add(permission);
        }
        appRoleRepository.save(role);
        // 记录权限变更（按 code 计算新增/移除）
        List<String> added = newCodes.stream()
                .filter(code -> !oldCodes.contains(code))
                .map(code -> permissionRepository.findByCode(code)
                        .map(FunctionPermission::getLabel)
                        .orElse(code))
                .collect(Collectors.toList());
        List<String> removed = oldCodes.stream()
                .filter(code -> !newCodes.contains(code))
                .map(code -> permissionRepository.findByCode(code)
                        .map(FunctionPermission::getLabel)
                        .orElse(code))
                .collect(Collectors.toList());
        String detail = "角色「" + role.getName() + "」权限变更";
        if (!added.isEmpty()) detail += "，新增：" + String.join("、", added);
        if (!removed.isEmpty()) detail += "，移除：" + String.join("、", removed);
        operationLogService.log("权限变更", "ROLE", id, detail);
    }

    private String normalizeDataScope(String dataScope) {
        if (dataScope == null || dataScope.isBlank()) {
            return DataScope.SELF.name();
        }
        try {
            return DataScope.valueOf(dataScope.trim().toUpperCase()).name();
        } catch (IllegalArgumentException ex) {
            throw new PolicyException("数据权限范围不合法");
        }
    }
}
