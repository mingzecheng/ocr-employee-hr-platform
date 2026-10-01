package com.hrplatform.workflow.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RequestPersistenceTest {
    @Autowired
    private RequestService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void persistsSubmitAndTwoApprovalTransitions() {
        RequestDtos.RequestData draft = service.create(
                new RequestDtos.CreateRequest("ONBOARDING", 42L,
                        objectMapper.createObjectNode().put("source", "ocr")), 7L);

        service.submit(draft.id(), 7L);
        service.approve(draft.id(), 8L, true, "部门同意");
        RequestDtos.RequestData approved = service.approve(draft.id(), 9L, true, "人事同意");

        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM hr_request WHERE id = ?", String.class, draft.id()))
                .isEqualTo("APPROVED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM approval_record WHERE request_id = ?", Integer.class, draft.id()))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE resource_id = ?", Integer.class, draft.id().toString()))
                .isEqualTo(3);
    }
}
