package com.hrplatform.employee;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmployeeProfileUpdateRequest(
        @NotBlank(message = "姓名不能为空")
        @Size(max = 128, message = "姓名不能超过 128 个字符")
        String name,
        @Pattern(regexp = "^\\s*$|^1[3-9]\\d{9}$", message = "联系电话格式错误")
        String phone
) {
}
