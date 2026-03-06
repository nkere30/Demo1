package com.task06;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.OperationType;

import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.annotations.events.DynamoDbTriggerEventSource;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;

import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(
        lambdaName = "audit_producer",
        roleName = "audit_producer-role",
        isPublishVersion = true,
        aliasName = "${lambdas_alias_name}",
        logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@DynamoDbTriggerEventSource(
        targetTable = "Configuration",
        batchSize = 1
)
@EnvironmentVariables(value = {
        @EnvironmentVariable(key = "table_name", value = "${target_table}"),
        @EnvironmentVariable(key = "region", value = "${region}")}
)
public class AuditProducer implements RequestHandler<DynamodbEvent, Void> {

    private static final String INSERT = OperationType.INSERT.toString();
    private static final String MODIFY = OperationType.MODIFY.toString();

    @Override
    public Void handleRequest(DynamodbEvent event, Context context) {
        String tableName = System.getenv("table_name");
        String region = System.getenv("region");

        AmazonDynamoDB dynamoDb = AmazonDynamoDBClientBuilder.defaultClient();
        for (DynamodbEvent.DynamodbStreamRecord record : event.getRecords()) {

            String eventName = record.getEventName();
            Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> newImage =
                    record.getDynamodb().getNewImage();
            String itemKey = newImage.get("key").getS();
            String value = newImage.get("value").getN();

            String id = UUID.randomUUID().toString();
            String modificationTime = Instant.now().toString();
            Map<String, AttributeValue> auditItem = new HashMap<>();
            auditItem.put("id", new AttributeValue().withS(id));
            auditItem.put("itemKey", new AttributeValue().withS(itemKey));
            auditItem.put("modificationTime", new AttributeValue().withS(modificationTime));

            if (INSERT.equals(eventName)) {
                Map<String, AttributeValue> newValueMap = new HashMap<>();
                newValueMap.put("key", new AttributeValue().withS(itemKey));
                newValueMap.put("value", new AttributeValue().withN(value));
                auditItem.put("newValue", new AttributeValue().withM(newValueMap));
            }
            else if (MODIFY.equals(eventName)) {
                Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> oldImage =
                        record.getDynamodb().getOldImage();
                String oldValue = oldImage.get("value").getN();
                auditItem.put("updatedAttribute", new AttributeValue().withS("value"));
                auditItem.put("oldValue", new AttributeValue().withN(oldValue));
                auditItem.put("newValue", new AttributeValue().withN(value));
            }
            PutItemRequest request = new PutItemRequest()
                    .withTableName(tableName)
                    .withItem(auditItem);

            dynamoDb.putItem(request);
        }
        return null;
    }
}