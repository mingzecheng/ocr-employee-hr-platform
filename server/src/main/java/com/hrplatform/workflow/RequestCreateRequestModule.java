package com.hrplatform.workflow;

import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.stereotype.Component;

@Component
public class RequestCreateRequestModule extends SimpleModule {
    public RequestCreateRequestModule() {
        super("hr-request-create");
        addDeserializer(RequestCreateRequest.class, new RequestCreateRequestDeserializer());
    }
}
