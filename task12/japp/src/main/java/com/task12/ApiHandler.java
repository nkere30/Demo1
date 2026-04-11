package com.task12;

import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProviderClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.model.RetentionSetting;
import com.task12.handler.*;
import com.task12.routing.RouteHandler;
import com.task12.util.ResponseUtil;

import java.util.HashMap;
import java.util.Map;

import static com.syndicate.deployment.model.environment.ValueTransformer.USER_POOL_NAME_TO_CLIENT_ID;
import static com.syndicate.deployment.model.environment.ValueTransformer.USER_POOL_NAME_TO_USER_POOL_ID;

@LambdaHandler(
        lambdaName = "api_handler",
        roleName = "api_handler-role",
        isPublishVersion = true,
        aliasName = "${lambdas_alias_name}",
        logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@DependsOn(resourceType = ResourceType.COGNITO_USER_POOL, name = "${booking_userpool}")
@EnvironmentVariables(value = {
        @EnvironmentVariable(key = "REGION", value = "${region}"),
        @EnvironmentVariable(key = "COGNITO_ID", value = "${pool_name}", valueTransformer = USER_POOL_NAME_TO_USER_POOL_ID),
        @EnvironmentVariable(key = "CLIENT_ID", value = "${pool_name}", valueTransformer = USER_POOL_NAME_TO_CLIENT_ID),
        @EnvironmentVariable(key = "TABLES_TABLE", value = "${tables_table}"),
        @EnvironmentVariable(key = "RESERVATIONS_TABLE", value = "${reservations_table}")
})
public class ApiHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final Map<String, RouteHandler> router;

    public ApiHandler() {
        this.router = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        AWSCognitoIdentityProvider awsCognitoIdentityProvider = AWSCognitoIdentityProviderClientBuilder.defaultClient();
        AmazonDynamoDB dynamoDBClient = AmazonDynamoDBClientBuilder.standard().build();
        router.put("POST /signup", new SignUpHandler(awsCognitoIdentityProvider, objectMapper));
        router.put("POST /signin", new SignInHandler(awsCognitoIdentityProvider, objectMapper));
        router.put("GET /tables", new GetTablesHandler(objectMapper, dynamoDBClient));
        router.put("POST /tables", new CreateTableHandler(objectMapper, dynamoDBClient));
        router.put("GET /tables/{tableId}", new GetTableByIdHandler(objectMapper, dynamoDBClient));
        router.put("POST /reservations", new CreateReservationHandler(objectMapper, dynamoDBClient));
        router.put("GET /reservations", new GetReservationsHandler(objectMapper, dynamoDBClient));
    }

    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        String path = (String) event.get("resource");
        String httpMethod = (String) event.get("httpMethod");
        context.getLogger().log("Path: " + path + " Method: " + httpMethod);
        String routeKey = httpMethod + " " + path;
        RouteHandler handler = router.get(routeKey);
        if(handler == null) {
            return ResponseUtil.response(400, "Bad Request");
        }
        return handler.handle(event, context);
    }
}
