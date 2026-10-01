package com.hrplatform.ocr;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
public class BusinessFieldMapper {
    private static final Pattern LABEL = Pattern.compile("^\\s*([^:：]+?)\\s*[:：]\\s*(.+?)\\s*$");
    private final double lowConfidenceThreshold;

    public BusinessFieldMapper() {
        this(0.85);
    }

    public BusinessFieldMapper(double lowConfidenceThreshold) {
        this.lowConfidenceThreshold = lowConfidenceThreshold;
    }

    public List<BusinessField> map(String documentType, List<OcrTextBlock> blocks) {
        Map<String, Rule> rules = rules(documentType);
        List<BusinessField> result = new ArrayList<>();
        for (OcrTextBlock block : blocks == null ? List.<OcrTextBlock>of() : blocks) {
            var matcher = LABEL.matcher(block.text());
            if (!matcher.matches()) {
                continue;
            }
            Rule rule = rules.get(matcher.group(1).trim());
            if (rule == null || result.stream().anyMatch(item -> item.fieldCode().equals(rule.code))) {
                continue;
            }
            String value = matcher.group(2).trim();
            boolean valid = rule.validator.test(value);
            result.add(new BusinessField(rule.code, value, block.confidence(), block.bbox(), block.pageNo(),
                    valid ? "PASSED" : "FAILED",
                    block.confidence() < lowConfidenceThreshold || !valid,
                    valid ? null : rule.message));
        }
        return result;
    }

    public FieldValidation validate(String fieldCode, String value) {
        for (Rule rule : rulesForCode(fieldCode)) {
            boolean valid = value != null && rule.validator.test(value.trim());
            return new FieldValidation(valid, valid ? null : rule.message);
        }
        return new FieldValidation(value != null && !value.isBlank(),
                value == null || value.isBlank() ? "字段不能为空" : null);
    }

    private List<Rule> rulesForCode(String fieldCode) {
        Map<String, Rule> all = new LinkedHashMap<>();
        common(all);
        add(all, "入职日期", "hire_date", this::isDate, "日期格式应为 yyyy-MM-dd");
        add(all, "生效日期", "effective_date", this::isDate, "日期格式应为 yyyy-MM-dd");
        add(all, "离职日期", "leave_date", this::isDate, "日期格式应为 yyyy-MM-dd");
        add(all, "部门", "department", this::isNonBlank, "部门不能为空");
        add(all, "岗位", "position", this::isNonBlank, "岗位不能为空");
        add(all, "原部门", "from_department", this::isNonBlank, "原部门不能为空");
        add(all, "新部门", "to_department", this::isNonBlank, "新部门不能为空");
        add(all, "原岗位", "from_position", this::isNonBlank, "原岗位不能为空");
        add(all, "新岗位", "to_position", this::isNonBlank, "新岗位不能为空");
        add(all, "调动原因", "transfer_reason", this::isNonBlank, "调动原因不能为空");
        add(all, "交接人", "handover_person", this::isNonBlank, "交接人不能为空");
        add(all, "交接状态", "handover_status", this::isNonBlank, "交接状态不能为空");
        add(all, "离职原因", "leave_reason", this::isNonBlank, "离职原因不能为空");
        return all.values().stream().filter(rule -> rule.code.equals(fieldCode)).toList();
    }

    private Map<String, Rule> rules(String documentType) {
        Map<String, Rule> rules = new LinkedHashMap<>();
        common(rules);
        if ("onboarding_form".equals(documentType)) {
            add(rules, "入职日期", "hire_date", this::isDate, "日期格式应为 yyyy-MM-dd");
            add(rules, "部门", "department", this::isNonBlank, "部门不能为空");
            add(rules, "岗位", "position", this::isNonBlank, "岗位不能为空");
        } else if ("transfer_form".equals(documentType)) {
            add(rules, "原部门", "from_department", this::isNonBlank, "原部门不能为空");
            add(rules, "新部门", "to_department", this::isNonBlank, "新部门不能为空");
            add(rules, "原岗位", "from_position", this::isNonBlank, "原岗位不能为空");
            add(rules, "新岗位", "to_position", this::isNonBlank, "新岗位不能为空");
            add(rules, "生效日期", "effective_date", this::isDate, "日期格式应为 yyyy-MM-dd");
            add(rules, "调动原因", "transfer_reason", this::isNonBlank, "调动原因不能为空");
        } else if ("offboarding_form".equals(documentType)) {
            add(rules, "离职日期", "leave_date", this::isDate, "日期格式应为 yyyy-MM-dd");
            add(rules, "交接人", "handover_person", this::isNonBlank, "交接人不能为空");
            add(rules, "交接状态", "handover_status", this::isNonBlank, "交接状态不能为空");
            add(rules, "离职原因", "leave_reason", this::isNonBlank, "离职原因不能为空");
        }
        return rules;
    }

    private void common(Map<String, Rule> rules) {
        add(rules, "员工姓名", "employee_name", this::isName, "员工姓名格式错误");
        add(rules, "姓名", "employee_name", this::isName, "员工姓名格式错误");
        add(rules, "员工编号", "employee_id", this::isEmployeeId, "员工编号格式错误");
        add(rules, "工号", "employee_id", this::isEmployeeId, "员工编号格式错误");
        add(rules, "联系电话", "phone", this::isPhone, "联系电话格式错误");
        add(rules, "手机号", "phone", this::isPhone, "联系电话格式错误");
    }

    private void add(Map<String, Rule> rules, String label, String code, Predicate<String> validator, String message) {
        rules.put(label, new Rule(code, validator, message));
    }

    private boolean isName(String value) { return value.matches("[\\u4e00-\\u9fffA-Za-z· ]+"); }
    private boolean isEmployeeId(String value) { return value.matches("[A-Za-z0-9][A-Za-z0-9_-]{1,31}"); }
    private boolean isPhone(String value) { return value.matches("1[3-9]\\d{9}"); }
    private boolean isDate(String value) {
        if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return false;
        }
        try {
            LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }
    private boolean isNonBlank(String value) { return !value.isBlank(); }

    private record Rule(String code, Predicate<String> validator, String message) { }

    public record FieldValidation(boolean passed, String message) { }
}
