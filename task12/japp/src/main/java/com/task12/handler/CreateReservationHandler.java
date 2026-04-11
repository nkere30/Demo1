package com.task12.handler;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.task12.routing.RouteHandler;
import com.task12.util.ResponseUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CreateReservationHandler implements RouteHandler {
    private final ObjectMapper objectMapper;
    private final AmazonDynamoDB amazonDynamoDB;

    public CreateReservationHandler (ObjectMapper objectMapper, AmazonDynamoDB amazonDynamoDB) {
        this.objectMapper = objectMapper;
        this.amazonDynamoDB = amazonDynamoDB;
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> requestEvent, Context context) {
        String tableName = System.getenv("RESERVATIONS_TABLE");
        String body = (String)requestEvent.get("body");
        try {
            Map<String, Object> bodyMap = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
            Integer tableNumber = (Integer) bodyMap.get("tableNumber");
            String tablesTableName = System.getenv("TABLES_TABLE");
            ScanRequest scanRequest = new ScanRequest()
                    .withTableName(tablesTableName)
                    .withFilterExpression("#num = :num")
                    .withExpressionAttributeNames(Map.of("#num", "number"))
                    .withExpressionAttributeValues(Map.of(
                            ":num", new AttributeValue().withN(tableNumber.toString())
                    ));
            ScanResult scanResult = amazonDynamoDB.scan(scanRequest);
            if (scanResult.getItems().isEmpty()) {
                return ResponseUtil.response(400, "Table not found");
            }
            String clientName = (String) bodyMap.get("clientName");
            String phoneNumber = (String) bodyMap.get("phoneNumber");
            String date = (String) bodyMap.get("date");
            String slotTimeStart = (String) bodyMap.get("slotTimeStart");
            String slotTimeEnd = (String) bodyMap.get("slotTimeEnd");
            Map<String, AttributeValue> item = new HashMap<>();
            ScanRequest reservationScan = new ScanRequest()
                    .withTableName(tableName)
                    .withFilterExpression("tableNumber = :tNum AND #d = :date")
                    .withExpressionAttributeNames(Map.of("#d", "date"))
                    .withExpressionAttributeValues(Map.of(
                            ":tNum", new AttributeValue().withN(tableNumber.toString()),
                            ":date", new AttributeValue().withS(date)
                    ));
            ScanResult existingReservations = amazonDynamoDB.scan(reservationScan);
            for (Map<String, AttributeValue> existing : existingReservations.getItems()) {
                String existingStart = existing.get("slotTimeStart").getS();
                String existingEnd = existing.get("slotTimeEnd").getS();
                if (slotTimeStart.compareTo(existingEnd) < 0 && slotTimeEnd.compareTo(existingStart) > 0) {
                    return ResponseUtil.response(400, "Reservation overlap");
                }
            }
            String reservationId = UUID.randomUUID().toString();
            item.put("id", new AttributeValue().withS(reservationId));
            item.put("tableNumber", new AttributeValue().withN(tableNumber.toString()));
            item.put("clientName", new AttributeValue().withS(clientName));
            item.put("phoneNumber", new AttributeValue().withS(phoneNumber));
            item.put("date", new AttributeValue().withS(date));
            item.put("slotTimeStart", new AttributeValue().withS(slotTimeStart));
            item.put("slotTimeEnd", new AttributeValue().withS(slotTimeEnd));
            PutItemRequest putItemRequest = new PutItemRequest().withTableName(tableName).withItem(item);
            amazonDynamoDB.putItem(putItemRequest);
            return ResponseUtil.response(200, objectMapper.writeValueAsString(Map.of("reservationId",  reservationId)));
        } catch (Exception e) {
            context.getLogger().log(e.getMessage());
            return ResponseUtil.response(400, "Bad Request");
        }
    }
}
