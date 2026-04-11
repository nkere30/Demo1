package com.task13.util;

import java.util.HashMap;
import java.util.Map;

public class ResponseUtil {
    public static Map<String, Object> response(int statusCode, String body) {
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("statusCode", statusCode);
        responseMap.put("body", body);
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Headers", "Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "*");
        headers.put("Accept-Version", "*");
        responseMap.put("headers", headers);
        return responseMap;
    }
}
