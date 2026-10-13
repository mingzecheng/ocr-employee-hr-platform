package com.hrplatform.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrplatform.workflow.RequestCreateRequestModule;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class GlobalExceptionHandlerTest {
    @Test
    void invalidRequestFieldReturnsBadRequestWithTraceId() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
                .registerModule(new RequestCreateRequestModule());
        MockMvc mvc = standaloneSetup(new RequestBodyController())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/request")
                        .header(TraceIdFilter.HEADER, "trace-invalid-json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestType\":\"LEAVE\",\"employeeId\":7,\"payloadJson\":\"{}\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.traceId").value("trace-invalid-json"));
    }

    @RestController
    static class RequestBodyController {
        @PostMapping("/request")
        ApiResponse<String> create(@RequestBody com.hrplatform.workflow.RequestCreateRequest request) {
            return ApiResponse.success(request.requestType(), "controller-test");
        }
    }
}
