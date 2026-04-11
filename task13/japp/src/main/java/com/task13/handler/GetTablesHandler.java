package com.task13.handler;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.task13.routing.RouteHandler;
import com.task13.util.DynamoDbUtil;
import com.task13.util.ResponseUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GetTablesHandler implements RouteHandler {
    private final ObjectMapper objectMapper;
    private final AmazonDynamoDB amazonDynamoDB;

    public GetTablesHandler(ObjectMapper objectMapper, AmazonDynamoDB amazonDynamoDB) {
        this.objectMapper = objectMapper;
        this.amazonDynamoDB = amazonDynamoDB;
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> requestEvent, Context context) {
        try {
            String tableName = System.getenv("TABLES_TABLE");
            ScanRequest request = new ScanRequest().withTableName(tableName);
            ScanResult result = amazonDynamoDB.scan(request);
            List<Map<String, AttributeValue>> items = result.getItems();
            List<Map<String, Object>> tables = new ArrayList<>();
            for (Map<String, AttributeValue> item : items) {
                tables.add(DynamoDbUtil.mapToTable(item));
            }
            return ResponseUtil.response(200, objectMapper.writeValueAsString(Map.of("tables", tables)));
        } catch (Exception e) {
            context.getLogger().log(e.getMessage());
            return ResponseUtil.response(400, "Bad Request");
        }
    }
}
