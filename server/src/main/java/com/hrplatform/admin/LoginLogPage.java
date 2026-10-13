package com.hrplatform.admin;

import java.util.List;

public record LoginLogPage(List<LoginLogItem> items, long total, int page, int size) {
}
