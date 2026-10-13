package com.hrplatform.ocr;

import com.hrplatform.archive.ArchiveCatalog;
import com.hrplatform.archive.ArchiveOcrSource;
import com.hrplatform.audit.OperationLogService;
import com.hrplatform.common.security.DataScope;
import com.hrplatform.employee.Employee;
import com.hrplatform.employee.EmployeeMapper;
import com.hrplatform.employee.EmployeeProfileMutation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * OCR 结果人工确认/拒绝服务（P0-2）。
 *
 * <p>状态机：ocr_binding.confirm_status = PENDING → CONFIRMED / REJECTED（终态）。
 *
 * <p>字段白名单（仅这些 fieldCode 参与确认流程）：
 * <ul>
 *   <li>{@code employee_name} → 回填 employee.name</li>
 *   <li>{@code phone} → 回填 employee.phone</li>
 *   <li>{@code employee_id} / {@code id_number} → 只进审计，不回写 employee 主档
 *       （工号是业务主键、身份证号按数据最小化原则不入库）</li>
 * </ul>
 *
 * <p>幂等：confirm_status 已是 CONFIRMED/REJECTED 时直接返回当前状态，不重复写 employee、不重复写审计。
 */
@Service
public class OcrConfirmService {

    /** 低置信度阈值，与 BusinessFieldMapper 默认阈值一致。 */
    private static final double LOW_CONFIDENCE = 0.8;

    /** 允许参与确认流程的 fieldCode 白名单。 */
    private static final Set<String> CONFIRMABLE_FIELDS = Set.of(
            "employee_name", "employee_id", "phone", "id_number");

    /** 真正回写 employee 主档的 fieldCode。 */
    private static final Set<String> BACKFILL_FIELDS = Set.of("employee_name", "phone");

    private final OcrBindingMapper bindingMapper;
    private final OcrClient ocrClient;
    private final BusinessFieldMapper fieldMapper;
    private final ArchiveCatalog archiveCatalog;
    private final EmployeeMapper employeeMapper;
    private final OperationLogService operationLogService;

    public OcrConfirmService(OcrBindingMapper bindingMapper, OcrClient ocrClient,
                             BusinessFieldMapper fieldMapper, ArchiveCatalog archiveCatalog,
                             EmployeeMapper employeeMapper, OperationLogService operationLogService) {
        this.bindingMapper = bindingMapper;
        this.ocrClient = ocrClient;
        this.fieldMapper = fieldMapper;
        this.archiveCatalog = archiveCatalog;
        this.employeeMapper = employeeMapper;
        this.operationLogService = operationLogService;
    }

    @Transactional
    public OcrConfirmResult confirm(Long bindingId, Long actorId, String note, DataScope scope) {
        OcrBinding binding = requireBinding(bindingId, scope);

        // 幂等：已确认直接返回
        if ("CONFIRMED".equals(binding.confirmStatus())) {
            return new OcrConfirmResult(bindingId, "CONFIRMED", null, false);
        }
        // 已拒绝为终态，不允许再确认
        if ("REJECTED".equals(binding.confirmStatus())) {
            throw new IllegalStateException("已拒绝的 OCR 结果不能再确认");
        }
        // OCR 任务本身必须成功
        if (binding.taskId() == null || binding.taskId().isBlank()
                || "FAILED".equals(binding.status()) || "UNAVAILABLE".equals(binding.status())) {
            throw new IllegalStateException("OCR 任务未成功，不能确认回填");
        }

        OcrTaskData task = ocrClient.getTask(binding.taskId());
        List<BusinessField> fields = mapFields(binding, task);
        Map<String, BusinessField> byCode = fields.stream()
                .filter(f -> f.fieldCode() != null)
                .collect(Collectors.toMap(BusinessField::fieldCode, f -> f, (a, b) -> a));

        // 已有人工修订记录的 fieldCode 集合
        Set<String> revised = bindingMapper.listRevisions(bindingId).stream()
                .map(OcrFieldRevision::fieldCode)
                .collect(Collectors.toCollection(HashSet::new));

        // 校验：白名单内字段必须 PASSED；低置信度字段必须已人工修订
        List<String> problems = fields.stream()
                .filter(f -> CONFIRMABLE_FIELDS.contains(f.fieldCode()))
                .filter(f -> {
                    boolean notPassed = !"PASSED".equals(f.validationStatus());
                    boolean lowConfNotRevised = f.confidence() < LOW_CONFIDENCE && !revised.contains(f.fieldCode());
                    return notPassed || lowConfNotRevised;
                })
                .map(f -> f.fieldCode() + "("
                        + ("PASSED".equals(f.validationStatus()) ? "低置信度未修订" : "校验未通过") + ")")
                .toList();
        if (!problems.isEmpty()) {
            throw new IllegalStateException("以下字段不满足确认条件: " + String.join(", ", problems));
        }

        // 解析目标员工
        ArchiveOcrSource source = archiveCatalog.findOcrSourceByVersionIdWithScope(
                binding.versionId(), null, null, DataScope.Type.ALL.name());
        if (source == null || source.employeeId() == null) {
            throw new IllegalStateException("OCR 绑定未关联到员工档案");
        }
        Employee employee = employeeMapper.lockById(source.employeeId());
        if (employee == null) {
            throw new IllegalStateException("员工档案不存在: " + source.employeeId());
        }

        // 计算回填后的姓名/电话（未识别到的字段保持原值）
        String candidateName = pickBackfillValue(byCode, "employee_name");
        String candidatePhone = pickBackfillValue(byCode, "phone");
        String newName = candidateName != null ? candidateName : employee.name();
        String newPhone = candidatePhone != null ? candidatePhone : employee.phone();

        String beforeSummary = "name=" + employee.name() + ";phone=" + employee.phone()
                + ";ocrEmployeeId=" + raw(byCode, "employee_id")
                + ";ocrIdNumber=" + (raw(byCode, "id_number") == null ? "null" : "[脱敏,不回写]");
        String afterSummary = "name=" + newName + ";phone=" + newPhone;

        boolean changed = !Objects.equals(employee.name(), newName)
                || !Objects.equals(employee.phone(), newPhone);
        if (changed) {
            if (employeeMapper.updateProfile(new EmployeeProfileMutation(employee.id(), newName, newPhone)) != 1) {
                throw new IllegalStateException("员工档案回填失败");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        if (bindingMapper.updateConfirmStatus(bindingId, "CONFIRMED", actorId, now, note) != 1) {
            throw new IllegalStateException("OCR 确认状态写入失败");
        }
        operationLogService.record(actorId, "OCR_CONFIRM", "OCR_BINDING", bindingId, "SUCCESS",
                beforeSummary, afterSummary);
        return new OcrConfirmResult(bindingId, "CONFIRMED", employee.id(), changed);
    }

    @Transactional
    public OcrConfirmResult reject(Long bindingId, Long actorId, String note, DataScope scope) {
        OcrBinding binding = requireBinding(bindingId, scope);

        if ("REJECTED".equals(binding.confirmStatus())) {
            return new OcrConfirmResult(bindingId, "REJECTED", null, false);
        }
        if ("CONFIRMED".equals(binding.confirmStatus())) {
            throw new IllegalStateException("已确认的 OCR 结果不能再拒绝");
        }

        LocalDateTime now = LocalDateTime.now();
        if (bindingMapper.updateConfirmStatus(bindingId, "REJECTED", actorId, now, note) != 1) {
            throw new IllegalStateException("OCR 拒绝状态写入失败");
        }
        operationLogService.record(actorId, "OCR_REJECT", "OCR_BINDING", bindingId, "SUCCESS",
                "confirm_status=PENDING", "confirm_status=REJECTED;note=" + note);
        return new OcrConfirmResult(bindingId, "REJECTED", null, false);
    }

    private OcrBinding requireBinding(Long bindingId, DataScope scope) {
        OcrBinding binding = bindingMapper.findByIdWithScope(bindingId, scope.employeeId(),
                scope.departmentId(), scope.type().name());
        if (binding == null) {
            throw new OcrBindingNotFoundException("OCR 绑定不存在或无权访问");
        }
        return binding;
    }

    private List<BusinessField> mapFields(OcrBinding binding, OcrTaskData task) {
        if (task == null || task.fields() == null) {
            return List.of();
        }
        // 与 OcrService.mapTask 保持一致：对每个 OCR 提取字段跑一次格式校验，
        // 生成带 validationStatus 的 BusinessField。
        List<BusinessField> result = new java.util.ArrayList<>();
        for (OcrRawField raw : task.fields()) {
            if (raw.fieldCode() == null) {
                continue;
            }
            BusinessFieldMapper.FieldValidation validation = fieldMapper.validate(raw.fieldCode(), raw.value());
            result.add(new BusinessField(raw.fieldCode(), raw.value(), raw.confidence(), raw.bbox(),
                    raw.pageNo(), validation.passed() ? "PASSED" : "FAILED",
                    raw.reviewRequired() || raw.confidence() < LOW_CONFIDENCE || !validation.passed(),
                    validation.message()));
        }
        return result;
    }

    /**
     * 取白名单字段的回填值：值非空、PASSED 才返回；否则返回 null（保持员工原值）。
     */
    private String pickBackfillValue(Map<String, BusinessField> byCode, String fieldCode) {
        BusinessField field = byCode.get(fieldCode);
        if (field == null || field.value() == null || field.value().isBlank()) {
            return null;
        }
        if (!"PASSED".equals(field.validationStatus())) {
            return null;
        }
        return field.value().trim();
    }

    private String raw(Map<String, BusinessField> byCode, String fieldCode) {
        BusinessField field = byCode.get(fieldCode);
        return field == null ? null : field.value();
    }
}
