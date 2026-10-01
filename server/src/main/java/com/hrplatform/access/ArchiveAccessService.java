package com.hrplatform.access;

import com.hrplatform.archive.ArchiveMapper;
import com.hrplatform.archive.ArchiveOcrSource;
import com.hrplatform.common.security.DataScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class ArchiveAccessService {
    private static final DateTimeFormatter NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final AccessMapper mapper;
    private final ArchiveMapper archiveMapper;

    @Autowired
    public ArchiveAccessService(AccessMapper mapper, ArchiveMapper archiveMapper) {
        this.mapper = mapper;
        this.archiveMapper = archiveMapper;
    }

    @Transactional
    public ArchiveAccessApplication create(AccessCreateRequest request, AccessActor actor) {
        if (request.dueAt().isBefore(LocalDateTime.now())) {
            throw new ArchiveAccessStateException("归还截止时间必须晚于当前时间");
        }
        if (actor.scope().type() == DataScope.Type.NONE) {
            throw new ArchiveAccessStateException("当前用户没有档案访问范围");
        }
        for (Long versionId : request.versionIds()) {
            ArchiveOcrSource source = archiveMapper.findOcrSourceByVersionIdWithScope(versionId,
                    actor.scope().employeeId(), actor.scope().departmentId(), actor.scope().type().name());
            if (source == null) {
                throw new ArchiveAccessStateException("档案版本不存在或无权申请: " + versionId);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        ArchiveAccessApplication application = new ArchiveAccessApplication(null,
                "ACC-" + NO_TIME.format(now) + "-" + UUID.randomUUID().toString().substring(0, 8),
                actor.userId(), actor.scope().departmentId(), request.purpose().trim(), request.useType().trim(),
                request.startAt() == null ? now : request.startAt(), request.dueAt(), "DRAFT", 0, now);
        ArchiveAccessApplication saved = mapper.insertApplication(application);
        ArchiveAccessApplication result = saved == null ? application : saved;
        for (Long versionId : request.versionIds()) {
            ArchiveOcrSource source = archiveMapper.findOcrSourceByVersionIdWithScope(versionId,
                    actor.scope().employeeId(), actor.scope().departmentId(), actor.scope().type().name());
            mapper.insertItem(new ArchiveAccessItem(null, result.id(), versionId,
                    source == null ? null : source.employeeId(),
                    source == null ? null : source.departmentId(), "REQUESTED"));
        }
        return result;
    }

    @Transactional
    public ArchiveAccessApplication submit(Long id, AccessActor actor) {
        ArchiveAccessApplication application = require(id, actor);
        return transition(application, ArchiveAccessStateMachine.submit(application.status()));
    }

    @Transactional
    public ArchiveAccessApplication approve(Long id, AccessActor actor) {
        ArchiveAccessApplication application = require(id, actor);
        if (!actor.roles().contains("HR_ADMIN") && !actor.roles().contains("SYSTEM_ADMIN")) {
            throw new ArchiveAccessStateException("只有人事管理员可以审批档案访问");
        }
        return transition(application, ArchiveAccessStateMachine.approve(application.status()));
    }

    @Transactional
    public ArchiveAccessApplication use(Long id, AccessActor actor) {
        ArchiveAccessApplication application = require(id, actor);
        ArchiveAccessApplication updated = transition(application, ArchiveAccessStateMachine.use(application.status()));
        mapper.insertUse(new ArchiveUseRecord(null, id, actor.userId(), LocalDateTime.now(), null, null, null));
        return updated;
    }

    @Transactional
    public ArchiveAccessApplication returnAccess(Long id, AccessActor actor, boolean abnormal, String remark) {
        ArchiveAccessApplication application = require(id, actor);
        ArchiveUseRecord active = mapper.findActiveUse(id);
        if (active == null) {
            throw new ArchiveAccessStateException("档案没有进行中的使用记录");
        }
        boolean overdue = LocalDateTime.now().isAfter(application.dueAt());
        boolean effectiveAbnormal = abnormal || overdue;
        ArchiveAccessApplication updated = transition(application,
                ArchiveAccessStateMachine.returnState(application.status(), effectiveAbnormal));
        mapper.closeUse(new ArchiveUseRecord(active.id(), id, active.operatorId(), active.usedAt(),
                LocalDateTime.now(), effectiveAbnormal ? "ABNORMAL" : "RETURNED", remark));
        return updated;
    }

    @Transactional(readOnly = true)
    public AccessPage list(String status, Long applicantId, int page, int pageSize, AccessActor actor) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        DataScope scope = actor.scope();
        Long scopedApplicant = scope.type() == DataScope.Type.EMPLOYEE ? scope.employeeId() : applicantId;
        Long scopedDepartment = scope.type() == DataScope.Type.DEPARTMENT ? scope.departmentId() : null;
        return new AccessPage(mapper.listApplications(scopedApplicant, status, scopedDepartment,
                scope.type().name(), (safePage - 1) * safeSize, safeSize),
                mapper.countApplications(scopedApplicant, status, scopedDepartment, scope.type().name()),
                safePage, safeSize);
    }

    private ArchiveAccessApplication require(Long id, AccessActor actor) {
        ArchiveAccessApplication application = mapper.findApplication(id, actor.scope().employeeId(),
                actor.scope().departmentId(), actor.scope().type().name());
        if (application == null) {
            throw new ArchiveAccessStateException("档案访问申请不存在或无权访问: " + id);
        }
        return application;
    }

    private ArchiveAccessApplication transition(ArchiveAccessApplication current, String next) {
        ArchiveAccessApplication updated = current.withState(next, current.versionNo() + 1);
        if (mapper.updateState(updated) != 1) {
            throw new ArchiveAccessStateException("访问申请版本已变化，请刷新后重试");
        }
        return updated;
    }
}
