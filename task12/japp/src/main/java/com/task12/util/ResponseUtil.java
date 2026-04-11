package com.task12.util;

import java.util.HashMap;
import java.util.Map;

public class ResponseUtil {
    public static Map<String, Object> response(int statusCode, String body) {
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("statusCode", statusCode);
        responseMap.put("body", body);
        return responseMap;
    }
}
