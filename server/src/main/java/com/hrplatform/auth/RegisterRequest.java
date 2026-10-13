package com.hrplatform.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 72, message = "密码长度必须为 8-72 位")
        String password,
        @NotBlank(message = "姓名不能为空")
        @Size(max = 128, message = "姓名不能超过 128 个字符")
        String name,
        @NotBlank(message = "预留手机号不能为空")
        @Pattern(regexp = "1[3-9]\\d{9}", message = "请输入 11 位有效手机号")
        String phone
) {
}
