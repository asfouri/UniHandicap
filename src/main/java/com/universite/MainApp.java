package com.universite;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import com.universite.util.AudioUtil;
import com.universite.util.SessionManager;
import com.universite.config.DatabaseConfig;

import java.sql.SQLException;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Load sounds at startup
        AudioUtil.chargerSons();

        // v6: Voice uses Vosk offline by default; Whisper only if key is present.
        String apiKey = System.getProperty("openai.apiKey");
        if (apiKey == null || apiKey.isBlank()) apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey != null && !apiKey.isBlank()) {
            System.out.println("[MainApp] Clé OpenAI détectée → Whisper activé.");
        } else {
            System.out.println("[MainApp] Pas de clé OpenAI → reconnaissance vocale Vosk (hors-ligne).");
        }

        // Test DB connection at startup, but keep the UI open so the user can fix credentials.
        try {
            DatabaseConfig.getConnection();
        } catch (SQLException e) {
            System.err.println("Echec de connexion a la base : " + e.getMessage());
        }

        // Start with Color Vision Test (before login)
        Parent root = FXMLLoader.load(getClass().getResource("/fxml/auth/ColorVisionTest.fxml"));
        Scene scene = new Scene(root, 900, 650);

        // Theme CSS (fallback to style.css)
        String cssPath = "/css/" + SessionManager.getThemeCSS() + ".css";
        if (getClass().getResource(cssPath) != null) {
            scene.getStylesheets().add(getClass().getResource(cssPath).toExternalForm());
        } else {
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        }

        // Apply zoom
        root.setScaleX(SessionManager.getZoomFactor());
        root.setScaleY(SessionManager.getZoomFactor());

        // Zoom shortcuts
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (!e.isControlDown()) return;
            if (e.getCode() == KeyCode.PLUS || e.getCode() == KeyCode.ADD
                    || (e.getCode() == KeyCode.EQUALS && e.isShiftDown())) {
                SessionManager.setZoomFactor(SessionManager.getZoomFactor() + 0.1);
                root.setScaleX(SessionManager.getZoomFactor());
                root.setScaleY(SessionManager.getZoomFactor());
                e.consume();
            } else if (e.getCode() == KeyCode.MINUS || e.getCode() == KeyCode.SUBTRACT) {
                SessionManager.setZoomFactor(SessionManager.getZoomFactor() - 0.1);
                root.setScaleX(SessionManager.getZoomFactor());
                root.setScaleY(SessionManager.getZoomFactor());
                e.consume();
            } else if (e.getCode() == KeyCode.DIGIT0 || e.getCode() == KeyCode.NUMPAD0) {
                SessionManager.setZoomFactor(1.0);
                root.setScaleX(SessionManager.getZoomFactor());
                root.setScaleY(SessionManager.getZoomFactor());
                e.consume();
            }
        });

        primaryStage.setTitle("Système de Gestion des Aménagements – Université");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(650);
        primaryStage.setResizable(true);
        primaryStage.setMaximized(true);
        primaryStage.show();
    }

    @Override
    public void stop() {
        SessionManager.clear();
        DatabaseConfig.closeConnection();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
