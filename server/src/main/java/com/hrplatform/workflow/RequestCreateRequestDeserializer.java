package com.hrplatform.workflow;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class RequestCreateRequestDeserializer extends JsonDeserializer<RequestCreateRequest> {
    private static final Set<String> COMMON_FIELDS = Set.of("requestType", "employeeId", "sourceVersionId");
    private static final Map<String, RequestType> REQUEST_TYPES = Map.of(
            "LEAVE", new RequestType(RequestCreateRequest.Leave.class,
                    Set.of("leaveType", "leaveStartDate", "leaveEndDate", "reason")),
            "ONBOARDING", new RequestType(RequestCreateRequest.Onboarding.class,
                    Set.of("targetDepartmentId", "targetPositionId", "effectiveDate", "contractType")),
            "TRANSFER", new RequestType(RequestCreateRequest.Transfer.class,
                    Set.of("targetDepartmentId", "targetPositionId", "effectiveDate", "reason")),
            "OFFBOARDING", new RequestType(RequestCreateRequest.Offboarding.class,
                    Set.of("leaveDate", "handoverPerson", "reason"))
    );

    @Override
    public RequestCreateRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode value = parser.getCodec().readTree(parser);
        if (!(value instanceof ObjectNode body)) {
            throw JsonMappingException.from(parser, "人事申请请求必须是 JSON 对象");
        }
        JsonNode typeValue = body.get("requestType");
        if (typeValue == null || !typeValue.isTextual() || typeValue.asText().isBlank()) {
            throw JsonMappingException.from(parser, "requestType 必须是 ONBOARDING、TRANSFER、LEAVE 或 OFFBOARDING");
        }

        String requestType = typeValue.asText().trim().toUpperCase(Locale.ROOT);
        RequestType type = REQUEST_TYPES.get(requestType);
        if (type == null) {
            throw JsonMappingException.from(parser, "requestType 必须是 ONBOARDING、TRANSFER、LEAVE 或 OFFBOARDING");
        }

        Set<String> allowedFields = new HashSet<>(COMMON_FIELDS);
        allowedFields.addAll(type.detailFields());
        Iterator<String> fieldNames = body.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (!allowedFields.contains(fieldName)) {
                throw JsonMappingException.from(parser, "字段 " + fieldName + " 不适用于 " + requestType + " 申请");
            }
        }

        body.remove("requestType");
        return parser.getCodec().treeToValue(body, type.dtoType());
    }

    private record RequestType(Class<? extends RequestCreateRequest> dtoType, Set<String> detailFields) {
    }
}
