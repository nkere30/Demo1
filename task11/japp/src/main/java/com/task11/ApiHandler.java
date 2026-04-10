package com.task11;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import static com.syndicate.deployment.model.environment.ValueTransformer.RDS_DB_CLUSTER_NAME_TO_ENDPOINT;
import static com.syndicate.deployment.model.environment.ValueTransformer.RDS_DB_CLUSTER_NAME_TO_MASTER_USER_SECRET_NAME;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@LambdaHandler(
        lambdaName = "api_handler",
        roleName = "api_handler-role",
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
public class ApiHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        String path = (String) event.get("path");
        String method = (String) event.get("httpMethod");
        Map<String, String> pathParams = (Map<String, String>) event.getOrDefault("pathParameters", new HashMap<>());
        if (pathParams == null) pathParams = new HashMap<>();
        String body = (String) event.get("body");

        try (Connection conn = DbConnectionHelper.getConnection()) {
            if (path.equals("/initdb") && method.equals("POST")) {
                return initDb(conn);
            } else if (path.startsWith("/shipments") && pathParams.containsKey("shipmentId")) {
                String shipmentId = pathParams.get("shipmentId");
                if (method.equals("GET")) return getShipment(conn, shipmentId);
                if (method.equals("PATCH")) return updateShipment(conn, shipmentId, body);
                if (method.equals("DELETE")) return deleteShipment(conn, shipmentId);
            } else if (path.equals("/shipments") && method.equals("POST")) {
                return createShipment(conn, body);
            } else if (path.startsWith("/carriers") && pathParams.containsKey("carrierId")) {
                String carrierId = pathParams.get("carrierId");
                if (method.equals("GET")) return getCarrier(conn, carrierId);
                if (method.equals("PATCH")) return updateCarrier(conn, carrierId, body);
                if (method.equals("DELETE")) return deleteCarrier(conn, carrierId);
            } else if (path.equals("/carriers") && method.equals("POST")) {
                return createCarrier(conn, body);
            } else if (path.startsWith("/statusupdates") && pathParams.containsKey("shipmentId")) {
                String shipmentId = pathParams.get("shipmentId");
                if (method.equals("GET")) return getStatusUpdates(conn, shipmentId);
            } else if (path.equals("/statusupdates") && method.equals("POST")) {
                return createStatusUpdate(conn, body);
            }
            return response(400, "Unknown route");
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return response(500, "Internal server error: " + e.getMessage());
        }
    }

    private Map<String, Object> initDb(Connection conn) throws Exception {
        conn.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS shipments (" +
                        "shipment_id VARCHAR(50) PRIMARY KEY," +
                        "order_id VARCHAR(50)," +
                        "origin VARCHAR(100)," +
                        "destination VARCHAR(100)," +
                        "weight_kg DECIMAL(10,2)," +
                        "created_at TIMESTAMPTZ)"
        );
        conn.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS carriers (" +
                        "carrier_id VARCHAR(50) PRIMARY KEY," +
                        "name VARCHAR(100)," +
                        "email VARCHAR(100)," +
                        "phone VARCHAR(20)," +
                        "is_active BOOLEAN)"
        );
        conn.createStatement().execute(
                "DO $$ BEGIN " +
                        "IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'statustype') THEN " +
                        "CREATE TYPE StatusType AS ENUM ('CREATED','IN_TRANSIT','DELAYED','DELIVERED','CANCELLED'); " +
                        "END IF; END $$"
        );
        conn.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS status_updates (" +
                        "update_id SERIAL PRIMARY KEY," +
                        "shipment_id VARCHAR(50) REFERENCES shipments(shipment_id)," +
                        "carrier_id VARCHAR(50) REFERENCES carriers(carrier_id)," +
                        "status StatusType," +
                        "location VARCHAR(100)," +
                        "notes TEXT," +
                        "timestamp TIMESTAMPTZ)"
        );
        return response(200, "Database initialized");
    }

    private Map<String, Object> getShipment(Connection conn, String shipmentId) throws Exception {
        PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM shipments WHERE shipment_id = ?");
        ps.setString(1, shipmentId);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            Map<String, Object> row = new HashMap<>();
            row.put("shipment_id", rs.getString("shipment_id"));
            row.put("order_id", rs.getString("order_id"));
            row.put("origin", rs.getString("origin"));
            row.put("destination", rs.getString("destination"));
            row.put("weight_kg", rs.getDouble("weight_kg"));
            row.put("created_at", rs.getTimestamp("created_at").toInstant().toString());
            return response(200, mapper.writeValueAsString(row));
        }
        return response(404, "Shipment not found");
    }

    private Map<String, Object> createShipment(Connection conn, String body) throws Exception {
        Map<String, Object> data = mapper.readValue(body, Map.class);
        PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO shipments(shipment_id,order_id,origin,destination,weight_kg,created_at) VALUES(?,?,?,?,?,?)");
        ps.setString(1, (String) data.get("shipment_id"));
        ps.setString(2, (String) data.get("order_id"));
        ps.setString(3, (String) data.get("origin"));
        ps.setString(4, (String) data.get("destination"));
        ps.setDouble(5, ((Number) data.get("weight_kg")).doubleValue());
        ps.setTimestamp(6, Timestamp.from(Instant.now()));
        ps.executeUpdate();
        return response(201, "Shipment created");
    }

    private Map<String, Object> updateShipment(Connection conn, String shipmentId, String body) throws Exception {
        Map<String, Object> data = mapper.readValue(body, Map.class);
        PreparedStatement ps = conn.prepareStatement(
                "UPDATE shipments SET order_id=?,origin=?,destination=?,weight_kg=? WHERE shipment_id=?");
        ps.setString(1, (String) data.get("order_id"));
        ps.setString(2, (String) data.get("origin"));
        ps.setString(3, (String) data.get("destination"));
        ps.setDouble(4, ((Number) data.get("weight_kg")).doubleValue());
        ps.setString(5, shipmentId);
        ps.executeUpdate();
        return response(200, "Shipment updated");
    }

    private Map<String, Object> deleteShipment(Connection conn, String shipmentId) throws Exception {
        PreparedStatement ps1 = conn.prepareStatement(
                "DELETE FROM status_updates WHERE shipment_id=?");
        ps1.setString(1, shipmentId);
        ps1.executeUpdate();

        PreparedStatement ps2 = conn.prepareStatement(
                "DELETE FROM shipments WHERE shipment_id=?");
        ps2.setString(1, shipmentId);
        ps2.executeUpdate();
        return response(200, "Shipment deleted");
    }

    private Map<String, Object> getCarrier(Connection conn, String carrierId) throws Exception {
        PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM carriers WHERE carrier_id = ?");
        ps.setString(1, carrierId);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            Map<String, Object> row = new HashMap<>();
            row.put("carrier_id", rs.getString("carrier_id"));
            row.put("name", rs.getString("name"));
            row.put("email", rs.getString("email"));
            row.put("phone", rs.getString("phone"));
            row.put("is_active", rs.getBoolean("is_active"));
            return response(200, mapper.writeValueAsString(row));
        }
        return response(404, "Carrier not found");
    }

    private Map<String, Object> createCarrier(Connection conn, String body) throws Exception {
        Map<String, Object> data = mapper.readValue(body, Map.class);
        PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO carriers(carrier_id,name,email,phone,is_active) VALUES(?,?,?,?,?)");
        ps.setString(1, (String) data.get("carrier_id"));
        ps.setString(2, (String) data.get("name"));
        ps.setString(3, (String) data.get("email"));
        ps.setString(4, (String) data.get("phone"));
        ps.setBoolean(5, (Boolean) data.get("is_active"));
        ps.executeUpdate();
        return response(201, "Carrier created");
    }

    private Map<String, Object> updateCarrier(Connection conn, String carrierId, String body) throws Exception {
        Map<String, Object> data = mapper.readValue(body, Map.class);
        PreparedStatement ps = conn.prepareStatement(
                "UPDATE carriers SET name=?,email=?,phone=?,is_active=? WHERE carrier_id=?");
        ps.setString(1, (String) data.get("name"));
        ps.setString(2, (String) data.get("email"));
        ps.setString(3, (String) data.get("phone"));
        ps.setBoolean(4, (Boolean) data.get("is_active"));
        ps.setString(5, carrierId);
        ps.executeUpdate();
        return response(200, "Carrier updated");
    }

    private Map<String, Object> deleteCarrier(Connection conn, String carrierId) throws Exception {
        PreparedStatement ps1 = conn.prepareStatement(
                "DELETE FROM status_updates WHERE carrier_id=?");
        ps1.setString(1, carrierId);
        ps1.executeUpdate();

        PreparedStatement ps2 = conn.prepareStatement(
                "DELETE FROM carriers WHERE carrier_id=?");
        ps2.setString(1, carrierId);
        ps2.executeUpdate();
        return response(200, "Carrier deleted");
    }

    private Map<String, Object> getStatusUpdates(Connection conn, String shipmentId) throws Exception {
        PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM status_updates WHERE shipment_id = ?");
        ps.setString(1, shipmentId);
        ResultSet rs = ps.executeQuery();
        List<Map<String, Object>> list = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new HashMap<>();
            row.put("update_id", rs.getInt("update_id"));
            row.put("shipment_id", rs.getString("shipment_id"));
            row.put("carrier_id", rs.getString("carrier_id"));
            row.put("status", rs.getString("status"));
            row.put("location", rs.getString("location"));
            row.put("notes", rs.getString("notes"));
            row.put("timestamp", rs.getTimestamp("timestamp").toInstant().toString());
            list.add(row);
        }
        return response(200, mapper.writeValueAsString(list));
    }

    private Map<String, Object> createStatusUpdate(Connection conn, String body) throws Exception {
        Map<String, Object> data = mapper.readValue(body, Map.class);
        PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO status_updates(shipment_id,carrier_id,status,location,notes,timestamp) VALUES(?,?,?::StatusType,?,?,?)");
        ps.setString(1, (String) data.get("shipment_id"));
        ps.setString(2, (String) data.get("carrier_id"));
        ps.setString(3, (String) data.get("status"));
        ps.setString(4, (String) data.get("location"));
        ps.setString(5, (String) data.get("notes"));
        ps.setTimestamp(6, Timestamp.from(Instant.now()));
        ps.executeUpdate();
        return response(201, "Status update created");
    }

    private Map<String, Object> response(int statusCode, String body) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("statusCode", statusCode);
        resp.put("body", body);
        return resp;
    }
}