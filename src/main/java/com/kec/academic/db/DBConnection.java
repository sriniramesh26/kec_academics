package com.kec.academic.db;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class DBConnection {

    private static final String PROPS_FILE = "/db.properties";
    private static final String URL;
    private static final String USER;
    private static final String PASSWORD;

    static {
        Properties p = new Properties();
        try (InputStream in = DBConnection.class.getResourceAsStream(PROPS_FILE)) {
            if (in != null) {
                p.load(in);
            }
        } catch (IOException e) {
            throw new ExceptionInInitializerError("Cannot read " + PROPS_FILE + ": " + e.getMessage());
        }
        URL = System.getProperty("db.url", p.getProperty("db.url", "jdbc:postgresql://localhost:5432/kec_academic"));
        USER = System.getProperty("db.user", p.getProperty("db.user", "postgres"));
        PASSWORD = System.getProperty("db.password", p.getProperty("db.password", ""));
    }

    private DBConnection() {
    }

    /** Returns a new connection. Callers must close it (use try-with-resources). */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}