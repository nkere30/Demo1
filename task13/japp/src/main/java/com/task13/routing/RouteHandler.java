package com.task13.routing;

import com.amazonaws.services.lambda.runtime.Context;
import java.util.Map;

public interface RouteHandler {
    Map<String, Object> handle(Map<String, Object> requestEvent, Context context);
}
