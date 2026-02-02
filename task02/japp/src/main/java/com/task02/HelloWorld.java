package com.task02;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;

import java.util.HashMap;
import java.util.Map;

@LambdaUrlConfig
@LambdaHandler(
    lambdaName = "hello_world",
	roleName = "hello_world-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
public class HelloWorld implements RequestHandler<Object, Map<String, Object>> {

    @Override
	public Map<String, Object> handleRequest(Object request, Context context) {
        System.out.println("Hello from Lambda");

        Map<String, Object> requestMap = (Map<String, Object>) request;
        String path = (String) requestMap.get("path");
        String method = (String) requestMap.get("httpMethod");

        Map<String, Object> resultMap = new HashMap<>();

        if("/hello".equals(path) && "GET".equals(method)){
            resultMap.put("statusCode", 200);
            resultMap.put("message", "Hello from Lambda");
            return resultMap;
        }

        resultMap.put("statusCode", 400);
        resultMap.put("message", "Bad request syntax or unsupported method. Request path: " +
                path +  ". HTTP method: " + method);

        return resultMap;
	}
}
