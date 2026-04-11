package com.task12.util;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;

public class DynamoDbUtil {
    public static Map<String, Object> mapToTable(Map<String, AttributeValue> item) {
        Map<String, Object> table = new HashMap<>();
        table.put("id", Integer.parseInt(item.get("id").getS()));
        table.put("number", Integer.parseInt(item.get("number").getN()));
        table.put("places", Integer.parseInt(item.get("places").getN()));
        table.put("isVip", item.get("isVip").getBOOL());
        if (item.containsKey("minOrder")) {
            table.put("minOrder", Integer.parseInt(item.get("minOrder").getN()));
        }
        return table;
    }
    public static Map<String, Object> mapToReservations(Map<String, AttributeValue> item) {
        Map<String, Object> reservations = new HashMap<>();
        reservations.put("tableNumber", Integer.parseInt(item.get("tableNumber").getN()));
        reservations.put("clientName", item.get("clientName").getS());
        reservations.put("phoneNumber", item.get("phoneNumber").getS());
        reservations.put("date", item.get("date").getS());
        reservations.put("slotTimeStart", item.get("slotTimeStart").getS());
        reservations.put("slotTimeEnd", item.get("slotTimeEnd").getS());
        return reservations;
    }

}
