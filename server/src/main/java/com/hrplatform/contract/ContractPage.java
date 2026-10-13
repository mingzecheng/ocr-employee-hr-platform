package com.hrplatform.contract;

import java.util.List;

public record ContractPage(List<ContractRecord> items, long total, int page, int pageSize) {
}
