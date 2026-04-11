package com.task12.handler;

import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.model.AdminInitiateAuthRequest;
import com.amazonaws.services.cognitoidp.model.AdminInitiateAuthResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.task12.routing.RouteHandler;
import com.task12.util.ResponseUtil;

import java.util.HashMap;
import java.util.Map;

public class SignInHandler implements RouteHandler {

    private final ObjectMapper objectMapper;
    private final AWSCognitoIdentityProvider awsCognitoIdentityProvider;

    public SignInHandler(AWSCognitoIdentityProvider cognitoClient, ObjectMapper objectMapper) {
        this.awsCognitoIdentityProvider = cognitoClient;
        this.objectMapper = objectMapper;
    }
    @Override
    public Map<String, Object> handle(Map<String, Object> requestEvent, Context context) {
        String userPoolId = System.getenv("COGNITO_ID");
        String clientId = System.getenv("CLIENT_ID");
        String body = (String) requestEvent.get("body");
        try {
            Map<String, Object> bodyMap = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
            String email = (String) bodyMap.get("email");
            String password = (String) bodyMap.get("password");
            Map<String, String> authParams = new HashMap<>();
            authParams.put("USERNAME", email);
            authParams.put("PASSWORD", password);
            AdminInitiateAuthRequest authRequest = new AdminInitiateAuthRequest()
                    .withUserPoolId(userPoolId)
                    .withClientId(clientId)
                    .withAuthParameters(authParams)
                    .withAuthFlow("ADMIN_USER_PASSWORD_AUTH");
            AdminInitiateAuthResult authResult = awsCognitoIdentityProvider.adminInitiateAuth(authRequest);
            String idToken = authResult.getAuthenticationResult().getIdToken();
            return ResponseUtil.response(200, objectMapper.writeValueAsString(Map.of("idToken", idToken)));
        } catch (Exception e) {
            context.getLogger().log(e.getMessage());
            return ResponseUtil.response(400, "Bad Request");
        }
    }
}
