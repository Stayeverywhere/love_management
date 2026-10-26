package com.lovemanagement.service;

import com.lovemanagement.domain.OperationLog;
import com.lovemanagement.repo.OperationLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 操作日志：记录角色/成员/权限变更，可追溯操作人。 */
@Service
public class OperationLogService {

    private final OperationLogRepository repository;
    private final CurrentUserService currentUserService;

    public OperationLogService(OperationLogRepository repository, CurrentUserService currentUserService) {
        this.repository = repository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public void log(String action, String targetType, Long targetId, String detail) {
        OperationLog log = new OperationLog();
        try {
            var account = currentUserService.requireCurrent();
            log.setOperatorId(account.getId());
            log.setOperatorName(account.getDisplayName());
        } catch (RuntimeException ex) {
            // 无登录上下文时记录为空（如系统初始化）
        }
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        repository.save(log);
    }

    public Page<OperationLog> page(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 50));
        return repository.findAllByOrderByIdDesc(pageable);
    }

    public Page<OperationLog> pageByTarget(String targetType, Long targetId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 50));
        return repository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, targetId, pageable);
    }
}
