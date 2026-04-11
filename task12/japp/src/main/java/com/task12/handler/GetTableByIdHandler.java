package com.task12.handler;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.task12.routing.RouteHandler;
import com.task12.util.DynamoDbUtil;
import com.task12.util.ResponseUtil;

import java.util.HashMap;
import java.util.Map;

public class GetTableByIdHandler implements RouteHandler {

    private final ObjectMapper objectMapper;
    private final AmazonDynamoDB amazonDynamoDB;

    public GetTableByIdHandler(ObjectMapper objectMapper, AmazonDynamoDB amazonDynamoDB) {
        this.objectMapper = objectMapper;
        this.amazonDynamoDB = amazonDynamoDB;
    }
    @Override
    public Map<String, Object> handle(Map<String, Object> requestEvent, Context context) {
        try {
            String tableName = System.getenv("TABLES_TABLE");
            @SuppressWarnings("unchecked")
            Map<String, String> pathParameters = (Map<String, String>) requestEvent.get("pathParameters");
            String tableId = pathParameters.get("tableId");
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("id", new AttributeValue().withS(tableId));
            GetItemRequest request = new GetItemRequest().withTableName(tableName).withKey(key);
            GetItemResult result = amazonDynamoDB.getItem(request);
            Map<String, AttributeValue> item = result.getItem();
            return ResponseUtil.response(200, objectMapper.writeValueAsString(DynamoDbUtil.mapToTable(item)));
        } catch (Exception e) {
            context.getLogger().log(e.getMessage());
            return ResponseUtil.response(400, "Bad Request");
        }
    }
}
