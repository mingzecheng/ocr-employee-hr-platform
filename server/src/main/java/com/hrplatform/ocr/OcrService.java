package com.hrplatform.ocr;

import com.hrplatform.audit.OperationLogService;
import com.hrplatform.common.security.DataScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OcrService {
    private final OcrClient client;
    private final OcrBindingMapper mapper;
    private final BusinessFieldMapper fieldMapper;
    private final OperationLogService operationLogService;

    public OcrService(OcrClient client, OcrBindingMapper mapper, BusinessFieldMapper fieldMapper) {
        this(client, mapper, fieldMapper, null);
    }

    @Autowired
    public OcrService(OcrClient client, OcrBindingMapper mapper, BusinessFieldMapper fieldMapper,
                      OperationLogService operationLogService) {
        this.client = client;
        this.mapper = mapper;
        this.fieldMapper = fieldMapper;
        this.operationLogService = operationLogService;
    }

    @Transactional
    public OcrBinding trigger(OcrTrigger trigger) {
        OcrBinding existing = mapper.findByVersionId(trigger.versionId());
        if (existing != null && !"FAILED".equals(existing.status())) {
            return existing;
        }
        LocalDateTime now = LocalDateTime.now();
        try {
            OcrTaskData task = client.create(new OcrCreateRequest(trigger.sourceObjectKey(), trigger.originalName(),
                    trigger.contentType(), trigger.size(), trigger.sha256(),
                    "employee_profile".equals(trigger.documentType()) ? "employee_profile" : null));
            List<BusinessField> fields = mapTask(trigger.documentType(), task);
            OcrBinding binding = bindingFromTask(trigger, task, fields, now);
            OcrBinding saved = persist(withId(binding, existing), existing == null);
            record(trigger.operatorId(), "OCR_TRIGGER", "OCR_BINDING", saved.id(), "SUCCESS");
            return saved;
        } catch (OcrClientException exception) {
            OcrBinding failed = new OcrBinding(null, trigger.versionId(), null, "FAILED", "unknown", 0L,
                    true, "OCR_UNAVAILABLE", now, trigger.documentType(), null, null, null, null,
                    null, null, exception.getMessage(), now, "PENDING", null, null, null);
            OcrBinding saved = persist(withId(failed, existing), existing == null);
            record(trigger.operatorId(), "OCR_TRIGGER", "OCR_BINDING", saved.id(), "FAILED");
            return saved;
        }
    }

    @Transactional
    public OcrResult triggerResult(OcrTrigger trigger) {
        OcrBinding binding = trigger(trigger);
        return result(binding);
    }

    @Transactional(readOnly = true)
    public OcrResult getResult(Long versionId, DataScope scope) {
        OcrBinding binding = mapper.findByVersionIdWithScope(versionId, scope.employeeId(),
                scope.departmentId(), scope.type().name());
        if (binding == null) {
            throw new OcrBindingNotFoundException("OCR 结果不存在或无权访问");
        }
        return result(binding);
    }

    @Transactional(readOnly = true)
    public byte[] preview(Long bindingId, DataScope scope) {
        OcrBinding binding = mapper.findByIdWithScope(bindingId, scope.employeeId(),
                scope.departmentId(), scope.type().name());
        if (binding == null || binding.taskId() == null || binding.taskId().isBlank()) {
            throw new OcrBindingNotFoundException("OCR 绑定不存在或无权访问");
        }
        return client.getPreview(binding.taskId());
    }

    @Transactional
    public OcrResult reviseField(Long bindingId, String fieldCode, String value, String reason,
                                 Long operatorId, DataScope scope) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("修订值不能为空");
        }
        OcrBinding binding = mapper.findByIdWithScope(bindingId, scope.employeeId(),
                scope.departmentId(), scope.type().name());
        if (binding == null || binding.taskId() == null || binding.taskId().isBlank()) {
            throw new OcrBindingNotFoundException("OCR 绑定不存在或无权访问");
        }
        OcrTaskData currentTask = client.getTask(binding.taskId());
        List<BusinessField> currentFields = mapTask(binding.documentType(), currentTask);
        BusinessField current = currentFields.stream()
                .filter(field -> field.fieldCode().equals(fieldCode))
                .findFirst()
                .orElseThrow(() -> new OcrFieldNotFoundException("OCR 字段不存在: " + fieldCode));
        OcrTaskData corrected = client.correctField(binding.taskId(), fieldCode, value.trim(),
                operatorId == null ? "unknown" : Long.toString(operatorId), reason);
        OcrFieldRevision revision = new OcrFieldRevision(null, binding.id(), fieldCode, current.value(),
                value.trim(), reason, operatorId, LocalDateTime.now());
        mapper.insertRevision(revision);
        record(operatorId, "OCR_FIELD_REVISE", "OCR_BINDING", binding.id(), "SUCCESS");
        return result(binding, corrected);
    }

    public OcrTaskData getTask(String taskId) {
        return client.getTask(taskId);
    }

    public BusinessFieldMapper fieldMapper() {
        return fieldMapper;
    }

    private OcrResult result(OcrBinding binding) {
        if (binding.taskId() == null || binding.taskId().isBlank()) {
            return result(binding, null);
        }
        try {
            return result(binding, client.getTask(binding.taskId()));
        } catch (OcrClientException exception) {
            OcrResult stored = result(binding, null);
            return new OcrResult(stored.bindingId(), stored.versionId(), stored.taskId(), "UNAVAILABLE",
                    stored.documentType(), stored.engineVersion(), stored.processingDurationMs(), true,
                    "OCR_RESULT_UNAVAILABLE", "OCR 识别结果暂不可用，请稍后刷新",
                    stored.previewUrl(), stored.textBlocks(), stored.rawFields(), stored.fields(), stored.revisions());
        }
    }

    private OcrResult result(OcrBinding binding, OcrTaskData task) {
        List<OcrTextBlock> blocks = task == null || task.textBlocks() == null
                ? List.of() : task.textBlocks();
        List<OcrRawField> rawFields = task == null || task.fields() == null
                ? List.of() : task.fields();
        List<BusinessField> fields = task == null ? List.of() : mapTask(binding.documentType(), task);
        return new OcrResult(binding.id(), binding.versionId(),
                task == null ? binding.taskId() : task.taskId(),
                task == null ? binding.status() : task.status(),
                binding.documentType(),
                task == null ? binding.engineVersion() : task.engineVersion(),
                task == null ? binding.processingDurationMs() : task.processingDurationMs(),
                task == null ? binding.reviewRequired() : fields.stream().anyMatch(BusinessField::reviewRequired),
                binding.errorCode(), task == null ? binding.errorMessage() : task.errorMessage(),
                binding.previewUrl(), blocks, rawFields, fields,
                binding.id() == null ? List.of() : mapper.listRevisions(binding.id()));
    }

    private OcrBinding bindingFromTask(OcrTrigger trigger, OcrTaskData task,
                                       List<BusinessField> fields, LocalDateTime now) {
        OcrPreview preview = task.detectionPreview();
        return new OcrBinding(null, trigger.versionId(), task.taskId(), task.status(), task.engineVersion(),
                task.processingDurationMs(), fields.stream().anyMatch(BusinessField::reviewRequired),
                "FAILED".equals(task.status()) ? "OCR_FAILED" : null, now, trigger.documentType(),
                preview == null ? null : preview.url(), preview == null ? null : preview.contentType(),
                preview == null ? null : preview.size(), preview == null ? null : preview.sha256(),
                preview == null ? null : preview.width(), preview == null ? null : preview.height(),
                task.errorMessage(), now, "PENDING", null, null, null);
    }

    private List<BusinessField> mapTask(String documentType, OcrTaskData task) {
        List<BusinessField> mapped = new ArrayList<>(fieldMapper.map(documentType,
                task == null ? List.of() : task.textBlocks()));
        if (task == null || task.fields() == null) {
            return mapped;
        }
        for (OcrRawField raw : task.fields()) {
            int index = -1;
            for (int i = 0; i < mapped.size(); i++) {
                if (mapped.get(i).fieldCode().equals(raw.fieldCode())) {
                    index = i;
                    break;
                }
            }
            BusinessFieldMapper.FieldValidation validation = fieldMapper.validate(raw.fieldCode(), raw.value());
            BusinessField field = new BusinessField(raw.fieldCode(), raw.value(), raw.confidence(), raw.bbox(),
                    raw.pageNo(), validation.passed() ? "PASSED" : "FAILED", raw.reviewRequired()
                    || raw.confidence() < 0.85 || !validation.passed(), validation.message());
            if (index >= 0) {
                mapped.set(index, field);
            } else {
                mapped.add(field);
            }
        }
        return mapped;
    }

    private void record(Long operatorId, String action, String objectType, Long objectId, String result) {
        if (operationLogService != null) {
            operationLogService.record(operatorId, action, objectType, objectId, result);
        }
    }

    private OcrBinding persist(OcrBinding binding, boolean insert) {
        if ((insert ? mapper.insert(binding) : mapper.update(binding)) != 1) {
            throw new IllegalStateException(insert ? "OCR 结果绑定写入失败" : "OCR 重试结果写入失败");
        }
        OcrBinding saved = mapper.findByVersionId(binding.versionId());
        if (saved == null) {
            throw new IllegalStateException("OCR 结果绑定已写入但无法读取");
        }
        return saved;
    }

    private OcrBinding withId(OcrBinding binding, OcrBinding existing) {
        if (existing == null) {
            return binding;
        }
        return new OcrBinding(existing.id(), binding.versionId(), binding.taskId(), binding.status(),
                binding.engineVersion(), binding.processingDurationMs(), binding.reviewRequired(),
                binding.errorCode(), existing.createdAt(), binding.documentType(), binding.previewUrl(),
                binding.previewContentType(), binding.previewSize(), binding.previewSha256(),
                binding.previewWidth(), binding.previewHeight(), binding.errorMessage(), binding.updatedAt(),
                // 重试 OCR 时保留已有人工确认状态，避免覆盖 confirm_status
                existing.confirmStatus(), existing.confirmedBy(), existing.confirmedAt(), existing.confirmNote());
    }
}
