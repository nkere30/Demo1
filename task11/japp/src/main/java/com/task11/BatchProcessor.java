package com.task11;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.events.S3EventSource;
import com.syndicate.deployment.annotations.events.S3Events;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import static com.syndicate.deployment.model.environment.ValueTransformer.RDS_DB_CLUSTER_NAME_TO_ENDPOINT;
import static com.syndicate.deployment.model.environment.ValueTransformer.RDS_DB_CLUSTER_NAME_TO_MASTER_USER_SECRET_NAME;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@LambdaHandler(
        lambdaName = "batch_processor",
        roleName = "batch_processor-role",
        isPublishVersion = true,
        aliasName = "${lambdas_alias_name}",
        logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
        subnetsIds = {"${lambda_sn_id}"},
        securityGroupIds = {"${logistic_sg_id}"}
)
@EnvironmentVariables(value = {
        @EnvironmentVariable(key = "DB_HOST", value = "logistic-cluster", valueTransformer = RDS_DB_CLUSTER_NAME_TO_ENDPOINT),
        @EnvironmentVariable(key = "SECRET_NAME", value = "logistic-cluster", valueTransformer = RDS_DB_CLUSTER_NAME_TO_MASTER_USER_SECRET_NAME)
})
@S3Events(@S3EventSource(targetBucket = "data-transfer-storage", events = {"s3:ObjectCreated:*"}))
public class BatchProcessor implements RequestHandler<S3Event, Void> {

    private final AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();

    public Void handleRequest(S3Event event, Context context) {
        event.getRecords().forEach(record -> {
            String bucket = record.getS3().getBucket().getName();
            String key = record.getS3().getObject().getKey();
            context.getLogger().log("Processing file: " + key + " from bucket: " + bucket);

            try (Connection conn = DbConnectionHelper.getConnection()) {
                S3Object s3Object = s3.getObject(bucket, key);
                BufferedReader reader = new BufferedReader(new InputStreamReader(s3Object.getObjectContent()));

                if (key.contains("shipments")) {
                    processShipments(conn, reader, context);
                } else if (key.contains("carriers")) {
                    processCarriers(conn, reader, context);
                } else if (key.contains("status_updates")) {
                    processStatusUpdates(conn, reader, context);
                }
            } catch (Exception e) {
                context.getLogger().log("Error processing file: " + e.getMessage());
            }
        });
        return null;
    }

    private void processShipments(Connection conn, BufferedReader reader, Context context) throws Exception {
        String line;
        boolean header = true;
        List<String[]> batch = new ArrayList<>();
        while ((line = reader.readLine()) != null) {
            if (header) { header = false; continue; }
            batch.add(parseCsvLine(line));
            if (batch.size() >= 500) {
                insertShipments(conn, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) insertShipments(conn, batch);
    }

    private void insertShipments(Connection conn, List<String[]> batch) throws Exception {
        StringBuilder sql = new StringBuilder(
                "INSERT INTO shipments(shipment_id,order_id,origin,destination,weight_kg,created_at) VALUES ");
        for (int i = 0; i < batch.size(); i++) {
            sql.append("(?,?,?,?,?,?)");
            if (i < batch.size() - 1) sql.append(",");
        }
        sql.append(" ON CONFLICT (shipment_id) DO NOTHING");
        PreparedStatement ps = conn.prepareStatement(sql.toString());
        int idx = 1;
        for (String[] row : batch) {
            ps.setString(idx++, row[0]);
            ps.setString(idx++, row[1]);
            ps.setString(idx++, row[2]);
            ps.setString(idx++, row[3]);
            ps.setDouble(idx++, Double.parseDouble(row[4]));
            ps.setTimestamp(idx++, Timestamp.valueOf(OffsetDateTime.parse(row[5]).toLocalDateTime()));
        }
        ps.executeUpdate();
    }

    private void processCarriers(Connection conn, BufferedReader reader, Context context) throws Exception {
        String line;
        boolean header = true;
        List<String[]> batch = new ArrayList<>();
        while ((line = reader.readLine()) != null) {
            if (header) { header = false; continue; }
            batch.add(parseCsvLine(line));
            if (batch.size() >= 500) {
                insertCarriers(conn, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) insertCarriers(conn, batch);
    }

    private void insertCarriers(Connection conn, List<String[]> batch) throws Exception {
        StringBuilder sql = new StringBuilder(
                "INSERT INTO carriers(carrier_id,name,email,phone,is_active) VALUES ");
        for (int i = 0; i < batch.size(); i++) {
            sql.append("(?,?,?,?,?)");
            if (i < batch.size() - 1) sql.append(",");
        }
        sql.append(" ON CONFLICT (carrier_id) DO NOTHING");
        PreparedStatement ps = conn.prepareStatement(sql.toString());
        int idx = 1;
        for (String[] row : batch) {
            ps.setString(idx++, row[0]);
            ps.setString(idx++, row[1]);
            ps.setString(idx++, row[2]);
            ps.setString(idx++, row[3]);
            ps.setBoolean(idx++, row[4].equalsIgnoreCase("true"));
        }
        ps.executeUpdate();
    }

    private void processStatusUpdates(Connection conn, BufferedReader reader, Context context) throws Exception {
        String line;
        boolean header = true;
        List<String[]> batch = new ArrayList<>();
        while ((line = reader.readLine()) != null) {
            if (header) { header = false; continue; }
            batch.add(parseCsvLine(line));
            if (batch.size() >= 500) {
                insertStatusUpdates(conn, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) insertStatusUpdates(conn, batch);
    }

    private void insertStatusUpdates(Connection conn, List<String[]> batch) throws Exception {
        StringBuilder sql = new StringBuilder(
                "INSERT INTO status_updates(shipment_id,carrier_id,status,location,notes,timestamp) VALUES ");
        for (int i = 0; i < batch.size(); i++) {
            sql.append("(?,?,?::StatusType,?,?,?)");
            if (i < batch.size() - 1) sql.append(",");
        }
        PreparedStatement ps = conn.prepareStatement(sql.toString());
        int idx = 1;
        for (String[] row : batch) {
            ps.setString(idx++, row[0]);
            ps.setString(idx++, row[1]);
            ps.setString(idx++, row[2]);
            ps.setString(idx++, row[3]);
            ps.setString(idx++, row[4]);
            ps.setTimestamp(idx++, Timestamp.valueOf(OffsetDateTime.parse(row[5]).toLocalDateTime()));
        }
        ps.executeUpdate();
    }

    private String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString().trim());
        return result.toArray(new String[0]);
    }
}