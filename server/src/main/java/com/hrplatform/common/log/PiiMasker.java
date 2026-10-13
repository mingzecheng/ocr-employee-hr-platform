package com.hrplatform.common.log;

import java.util.regex.Pattern;

/**
 * 日志层 PII 脱敏工具。只在写日志时调用，不改变 API 响应内容。
 *
 * <p>规则：
 * <ul>
 *     <li>姓名：保留首字，两字姓名其余打码；三字及以上保留首尾各一字。</li>
 *     <li>手机号：保留前 3 后 4。</li>
 *     <li>身份证号：保留前 4 后 4。</li>
 *     <li>对象 key（如 Minio objectKey）：保留前两段路径，其余打码。</li>
 *     <li>{@link #maskText(String)}：对自由文本（异常 message）正则脱敏手机号与身份证号。</li>
 * </ul>
 */
public final class PiiMasker {

    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern ID_NUMBER = Pattern.compile("\\d{17}[\\dXx]");

    private PiiMasker() {
    }

    public static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        int len = name.length();
        if (len == 1) {
            return "*";
        }
        if (len == 2) {
            return name.charAt(0) + "*";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(name.charAt(0));
        for (int i = 1; i < len - 1; i++) {
            sb.append('*');
        }
        sb.append(name.charAt(len - 1));
        return sb.toString();
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return phone;
        }
        int len = phone.length();
        if (len < 7) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(len - 4);
    }

    public static String maskIdNumber(String idNumber) {
        if (idNumber == null || idNumber.isEmpty()) {
            return idNumber;
        }
        int len = idNumber.length();
        if (len < 8) {
            return "****";
        }
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < len - 8; i++) {
            stars.append('*');
        }
        return idNumber.substring(0, 4) + stars + idNumber.substring(len - 4);
    }

    public static String maskObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isEmpty()) {
            return objectKey;
        }
        String[] segments = objectKey.split("/");
        if (segments.length <= 2) {
            return objectKey;
        }
        return segments[0] + "/" + segments[1] + "/***";
    }

    /**
     * 对异常 message 等自由文本做正则脱敏，覆盖手机号与身份证号。姓名在自由文本中无法
     * 可靠识别，结构化日志字段应在调用处显式调用 {@link #maskName(String)}。
     */
    public static String maskText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String masked = ID_NUMBER.matcher(text).replaceAll(match -> maskIdNumber(match.group()));
        masked = PHONE.matcher(masked).replaceAll(match -> maskPhone(match.group()));
        return masked;
    }
}
