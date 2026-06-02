package com.universite.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConfig {

    private static final String HOST = System.getProperty("db.host", "localhost");
    private static final String PORT = System.getProperty("db.port", "3306");
    private static final String DATABASE = System.getProperty("db.name", "universite_accessibilite");
    private static final String USERNAME = System.getProperty("db.user", "root");
    private static final String PASSWORD = resolvePassword();

    private static final String URL =
        "jdbc:mysql://" + HOST + ":" + PORT + "/" + DATABASE
            + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true";

    private static Connection connection = null;

    private static String resolvePassword() {
        String fromProp = System.getProperty("db.password");
        if (fromProp != null) return fromProp;

        String fromEnv = System.getenv("DB_PASSWORD");
        if (fromEnv != null) return fromEnv;

        String fromMySqlEnv = System.getenv("MYSQL_PASSWORD");
        if (fromMySqlEnv != null) return fromMySqlEnv;

        return "";
    }

    public static Connection getConnection() throws SQLException {
        try {
            if (connection == null || connection.isClosed()) {
                Class.forName("com.mysql.cj.jdbc.Driver");
                connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
                System.out.println("Connexion a MySQL etablie.");
            }
            return connection;
        } catch (ClassNotFoundException e) {
            throw new SQLException("Driver MySQL introuvable", e);
        }
    }

    public static void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Connexion fermee.");
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la fermeture : " + e.getMessage());
        }
    }
}
