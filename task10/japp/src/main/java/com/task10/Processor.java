package com.task10;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.TracingMode;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(
    lambdaName = "processor",
	roleName = "processor-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
        tracingMode = TracingMode.Active
)
@LambdaUrlConfig(
        authType = AuthType.NONE,
        invokeMode = InvokeMode.BUFFERED
)
@DependsOn(
        name = "Weather",
        resourceType = ResourceType.DYNAMODB_TABLE
)
@EnvironmentVariables(
        @EnvironmentVariable(key = "target_table", value = "${target_table}")
)
public class Processor implements RequestHandler<Object, Map<String, Object>> {

    private static final String API_URL = "https://api.open-meteo.com/v1/forecast?latitude=52.52&longitude=13.41&current=temperature_2m,wind_speed_10m&hourly=temperature_2m,relative_humidity_2m,wind_speed_10m";
	private final ObjectMapper objectMapper = new ObjectMapper();
    private final DynamoDbClient dynamoDbClient = DynamoDbClient.create();

    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Object request, Context context) {
		Map<String, Object> resultMap = new HashMap<String, Object>();
        try {
            // Fetch Weather String from Open-meteo
            String weatherJson = fetchWeather();
            // Parse with mapper and save to DynamoDB
            Map<String, Object> weatherMap = objectMapper.readValue(weatherJson, Map.class);
            saveWeatherToDynamoDB(weatherMap);
            resultMap.put("statusCode", 200);
            resultMap.put("body", weatherJson);
        }catch (Exception e) {
            context.getLogger().log("Error:" + e.getMessage());
            resultMap.put("statusCode", 500);
            resultMap.put("body", "{\"message\":\"" + e.getMessage() + "\"}");
        }
		return resultMap;
	}

    private String fetchWeather() throws Exception {
        URL url = new URL(API_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        return response.toString();
    }

    private void saveWeatherToDynamoDB(Map<String, Object> weatherMap) {
        String tableName = System.getenv("target_table");
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(UUID.randomUUID().toString()).build());
        item.put("forecast", toAttributeValue(weatherMap));
        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build());
    }

    @SuppressWarnings("unchecked")
    private AttributeValue toAttributeValue(Object obj) {
        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            Map<String, AttributeValue> avMap = new HashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                avMap.put(entry.getKey(), toAttributeValue(entry.getValue()));
            }
            return AttributeValue.builder().m(avMap).build();
        } else if (obj instanceof List) {
            List<Object> list = (List<Object>) obj;
            List<AttributeValue> avList = new java.util.ArrayList<>();
            for (Object item : list) {
                avList.add(toAttributeValue(item));
            }
            return AttributeValue.builder().l(avList).build();
        } else if (obj instanceof Number) {
            return AttributeValue.builder().n(obj.toString()).build();
        } else if (obj instanceof Boolean) {
            return AttributeValue.builder().bool((Boolean) obj).build();
        } else if (obj == null) {
            return AttributeValue.builder().nul(true).build();
        } else {
            return AttributeValue.builder().s(obj.toString()).build();
        }
    }
}
