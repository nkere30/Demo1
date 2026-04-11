package com.task12.handler;

import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.model.AdminCreateUserRequest;
import com.amazonaws.services.cognitoidp.model.AdminSetUserPasswordRequest;
import com.amazonaws.services.cognitoidp.model.AttributeType;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.task12.routing.RouteHandler;
import com.task12.util.ResponseUtil;

import java.util.Map;

public class SignUpHandler implements RouteHandler {

    private final ObjectMapper objectMapper;
    private final AWSCognitoIdentityProvider awsCognitoIdentityProvider;

    public SignUpHandler(AWSCognitoIdentityProvider cognitoClient, ObjectMapper objectMapper) {
        this.awsCognitoIdentityProvider = cognitoClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> requestEvent, Context context) {
        String body = (String) requestEvent.get("body");
        try {
            Map<String, Object> bodyMap = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {
            });
            String firstName = (String) bodyMap.get("firstName");
            String lastName = (String) bodyMap.get("lastName");
            String email = (String) bodyMap.get("email");
            String password = (String) bodyMap.get("password");
            String userPoolId = System.getenv("COGNITO_ID");
            AttributeType firstNameAttr = new AttributeType().withName("given_name").withValue(firstName);
            AttributeType lastNameAttr = new AttributeType().withName("family_name").withValue(lastName);
            AttributeType emailAttr = new AttributeType().withName("email").withValue(email);
            AdminCreateUserRequest request = new AdminCreateUserRequest()
                    .withUserPoolId(userPoolId)
                    .withUsername(email)
                    .withTemporaryPassword(password)
                    .withUserAttributes(firstNameAttr, lastNameAttr, emailAttr)
                    .withMessageAction("SUPPRESS");
            awsCognitoIdentityProvider.adminCreateUser(request);
            AdminSetUserPasswordRequest setUserPasswordRequest = new AdminSetUserPasswordRequest()
                    .withUserPoolId(userPoolId)
                    .withUsername(email)
                    .withPassword(password)
                    .withPermanent(true);
            awsCognitoIdentityProvider.adminSetUserPassword(setUserPasswordRequest);
            return ResponseUtil.response(200, "OK");
        } catch (Exception e) {
            context.getLogger().log(e.getMessage());
            return ResponseUtil.response(400, "Bad Request");
        }
    }
}
