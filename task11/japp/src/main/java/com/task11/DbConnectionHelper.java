package com.task11;

import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DriverManager;

public class DbConnectionHelper {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static Connection getConnection() throws Exception {
        String dbHost = System.getenv("DB_HOST");
        String secretName = System.getenv("SECRET_NAME");

        AWSSecretsManager client = AWSSecretsManagerClientBuilder.defaultClient();
        String secretJson = client.getSecretValue(
                new GetSecretValueRequest().withSecretId(secretName)
        ).getSecretString();

        JsonNode secret = mapper.readTree(secretJson);
        String username = secret.get("username").asText();
        String password = secret.get("password").asText();

        String url = "jdbc:postgresql://" + dbHost + ":5432/logisticdb";
        return DriverManager.getConnection(url, username, password);
    }
}