package com.task02;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.lambda.url.AuthType;

import java.util.HashMap;
import java.util.Map;

@LambdaUrlConfig(authType = AuthType.NONE)
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

        Map<String, Object> requestMap = (Map<String, Object>) request;

        Map<String, Object> requestContext =
                (Map<String, Object>) requestMap.get("requestContext");
        Map<String, Object> http =
                (Map<String, Object>) requestContext.get("http");

        String path = (String) http.get("path");
        String method = (String) http.get("method");

        Map<String, Object> response = new HashMap<>();

        if ("/hello".equals(path) && "GET".equals(method)) {
            response.put(
                    "body",
                    "{\"statusCode\":200,\"message\":\"Hello from Lambda\"}"
            );
            return response;
        }

        response.put(
                "body",
                "{\"statusCode\":400,\"message\":\"Bad request syntax or unsupported method. Request path: "
                        + path + ". HTTP method: " + method + "\"}"
        );

        return response;
    }

}
