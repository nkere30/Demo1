package com.task05;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(
        lambdaName = "api_handler",
        roleName = "api_handler-role",
        isPublishVersion = true,
        aliasName = "${lambdas_alias_name}",
        logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@EnvironmentVariables(value = {
        @EnvironmentVariable(key = "table_name", value = "${target_table}"),
        @EnvironmentVariable(key = "region", value = "${region}")
})
public class ApiHandler implements RequestHandler<Object, Map<String, Object>> {

    public Map<String, Object> handleRequest(Object request, Context context) {

        Map<String, Object> requestMap = (Map<String, Object>) request;

        String tableName = System.getenv("table_name");
        String region = System.getenv("region");

        Map<String, Object> bodyMap;

        if (requestMap.containsKey("body")) {
            String bodyJson = (String) requestMap.get("body");
            try {
                bodyMap = new ObjectMapper().readValue(bodyJson, Map.class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            bodyMap = requestMap;
        }

        Number principalIdNum = (Number) bodyMap.get("principalId");
        Integer principalId = principalIdNum.intValue();

        Map<String, Object> content = (Map<String, Object>) bodyMap.get("content");

        Map<String, AttributeValue> body = new HashMap<>();

        for (Map.Entry<String, Object> entry : content.entrySet()) {
            body.put(entry.getKey(), AttributeValue.builder().s(entry.getValue().toString()).build());
        }

        String id = UUID.randomUUID().toString();
        String createdAt = Instant.now().toString();

        try (DynamoDbClient dynamoDb = DynamoDbClient.builder()
                .region(software.amazon.awssdk.regions.Region.of(region))
                .build()) {

            Map<String, AttributeValue> item = new HashMap<>();

            item.put("id", AttributeValue.builder().s(id).build());
            item.put("principalId", AttributeValue.builder().n(principalId.toString()).build());
            item.put("createdAt", AttributeValue.builder().s(createdAt).build());
            item.put("body", AttributeValue.builder().m(body).build());

            PutItemRequest requestDb = PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .build();

            dynamoDb.putItem(requestDb);
        }

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", id);
        event.put("principalId", principalId);
        event.put("createdAt", createdAt);
        event.put("body", content);

        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("statusCode", 201);
        responseBody.put("event", event);

        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", 201);

        try {
            response.put("body", new ObjectMapper().writeValueAsString(responseBody));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return response;
    }
}