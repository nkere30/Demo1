package com.task09;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaLayer;
import com.syndicate.deployment.model.ArtifactExtension;
import com.syndicate.deployment.model.DeploymentRuntime;
import com.syndicate.deployment.model.RetentionSetting;
import com.task09.weather.OpenMeteoClient;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.lambda.url.AuthType;
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
@LambdaLayer(
        layerName = "weather_sdk",
        libraries = {"lib/weather-sdk-1.0.0.jar"},
        runtime = DeploymentRuntime.JAVA11,
        artifactExtension = ArtifactExtension.ZIP
)
@LambdaUrlConfig(
        authType = AuthType.NONE
)
public class ApiHandler implements RequestHandler<Object, Map<String, Object>> {

    public Map<String, Object> handleRequest(Object request, Context context) {
        Map<String, Object> resultMap = new HashMap<>();
        try {
            Map<String, Object> req = (Map<String, Object>) request;
            String path = (String) req.get("path");
            String method = (String) req.get("httpMethod");

            if ("/weather".equals(path) && "GET".equals(method)) {
                OpenMeteoClient client = new OpenMeteoClient();
                String weather = client.getWeather();
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> weatherMap = mapper.readValue(weather, Map.class);
                resultMap.put("statusCode", 200);
                resultMap.putAll(weatherMap);
            } else {
                resultMap.put("statusCode", 400);
                resultMap.put("message", "Bad request syntax or unsupported method. Request path: " + path + ". HTTP method: " + method);
            }
        } catch (Exception e) {
            resultMap.put("statusCode", 400);
            resultMap.put("body", "Bad request syntax or unsupported method.");
        }
        return resultMap;
    }
}
