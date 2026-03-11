package com.task09;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaLayer;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.ArtifactExtension;
import com.syndicate.deployment.model.DeploymentRuntime;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import com.task09.weather.OpenMeteoClient;
import java.util.HashMap;
import java.util.Map;

@LambdaHandler(
        lambdaName = "api_handler",
        roleName = "api_handler-role",
        layers = {"weather_sdk"},
        isPublishVersion = true,
        aliasName = "${lambdas_alias_name}",
        logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@LambdaUrlConfig(
        authType = AuthType.NONE,
        invokeMode = InvokeMode.BUFFERED
)
@LambdaLayer(
        layerName = "weather_sdk",
        libraries = {"lib/weather-sdk-1.0.0.jar"},
        runtime = DeploymentRuntime.JAVA11,
        artifactExtension = ArtifactExtension.ZIP
)
public class ApiHandler implements RequestHandler<Object, Map<String, Object>> {

    public Map<String, Object> handleRequest(Object request, Context context) {
        Map<String, Object> resultMap = new HashMap<>();
        try {
            Map<String, Object> req = (Map<String, Object>) request;
            String path = (String) req.get("rawPath");
            Map<String, Object> requestContext = (Map<String, Object>) req.get("requestContext");
            Map<String, Object> http = (Map<String, Object>) requestContext.get("http");
            String method = (String) http.get("method");

            if ("/weather".equals(path) && "GET".equals(method)) {
                OpenMeteoClient client = new OpenMeteoClient();
                String weather = client.getWeather();
                resultMap.put("statusCode", 200);
                resultMap.put("headers", Map.of("Content-Type", "application/json"));
                resultMap.put("body", weather);
            } else {
                resultMap.put("statusCode", 400);
                resultMap.put("headers", Map.of("Content-Type", "application/json"));
                resultMap.put("body", "{\"statusCode\":400,\"message\":\"Bad request syntax or unsupported method. Request path: " + path + ". HTTP method: " + method + "\"}");
            }
        } catch (Exception e) {
            resultMap.put("statusCode", 400);
            resultMap.put("headers", Map.of("Content-Type", "application/json"));
            resultMap.put("body", "{\"statusCode\":400,\"message\":\"" + e.getMessage() + "\"}");
        }
        return resultMap;
    }
}