package org.example.vertxDemo.Utils;

public class DBQueries {


    //Authentication queries
    public static final String login        = "SELECT * FROM users WHERE email = $1 AND password = $2";
    public static final String registration = "INSERT INTO users (email, password, username) VALUES ($1, $2, $3)";


    //Credential queries
    public static final String createCredentials    = "INSERT INTO credentials (credential_name, systemtype, data) VALUES ($1, $2, $3::jsonb) RETURNING id";
    public static final String selectCredentialById = "SELECT id, credential_name, systemtype, data FROM credentials WHERE id = $1";
    public static final String selectAllCredentials = "SELECT id, credential_name, systemtype, data FROM credentials";
    public static final String updateCredential     = "UPDATE credentials SET credential_name = COALESCE($1, credential_name), systemtype = COALESCE($2, systemtype), data = COALESCE($3::jsonb, data) WHERE id = $4";
    public static final String deleteCredentialById = "DELETE FROM credentials WHERE id = $1";
    public static final String getAllDiscovery      = "SELECT id, ip_address, port, created_at, discovery_name FROM discovery";
    public static final String fetchCredentialsQuery= "SELECT c.id, c.systemtype, c.data FROM credentials c " +
            "JOIN discovery_credentials dc ON c.id = dc.credential_id " +
            "WHERE dc.discovery_id = $1";

    //Discovery Queries

    public static final String createDiscovery = "INSERT INTO discovery (discovery_name, ip_address, port) VALUES ($1, $2, $3) RETURNING id";
    public static final String getDiscoveryById= "SELECT id, ip_address, port, created_at, discovery_name FROM discovery WHERE id = $1";


    //Discovery_credential Queries
    public static final String createDiscoveryCredential= "INSERT INTO discovery_credentials (discovery_id, credential_id) VALUES ($1, $2)";

    //Discovery Result Queries

    public static final String createDiscovaryResult = "INSERT INTO discovery_result (discovary_id, credential_id, status, response,ip_address) " +
            "VALUES ($1, $2, $3, $4, $5)";
}
