package com.task12.handler;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.task12.routing.RouteHandler;
import com.task12.util.ResponseUtil;

import java.util.HashMap;
import java.util.Map;

public class CreateTableHandler implements RouteHandler {
    private final ObjectMapper objectMapper;
    private final AmazonDynamoDB amazonDynamoDB;

    public CreateTableHandler(ObjectMapper objectMapper, AmazonDynamoDB amazonDynamoDB) {
        this.objectMapper = objectMapper;
        this.amazonDynamoDB = amazonDynamoDB;
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> requestEvent, Context context) {
        String tableName = System.getenv("TABLES_TABLE");
        String body = (String) requestEvent.get("body");
        try {
            Map<String, Object> bodyMap = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
            Integer id = (Integer) bodyMap.get("id");
            Integer number = (Integer) bodyMap.get("number");
            Integer places = (Integer) bodyMap.get("places");
            Boolean isVip = (Boolean) bodyMap.get("isVip");
            Integer minOrder = bodyMap.containsKey("minOrder") ? (Integer) bodyMap.get("minOrder") : null;
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("id", new AttributeValue().withS(id.toString()));
            item.put("number", new AttributeValue().withN(number.toString()));
            item.put("places", new AttributeValue().withN(places.toString()));
            item.put("isVip", new AttributeValue().withBOOL(isVip));
            if(minOrder != null) item.put("minOrder", new AttributeValue().withN(minOrder.toString()));
            PutItemRequest putItemRequest = new PutItemRequest().withTableName(tableName).withItem(item);
            amazonDynamoDB.putItem(putItemRequest);
            return ResponseUtil.response(200, objectMapper.writeValueAsString(Map.of("id", id)));
        } catch (Exception e) {
            context.getLogger().log(e.getMessage());
            return ResponseUtil.response(400, "Bad Request");
        }
    }
}
