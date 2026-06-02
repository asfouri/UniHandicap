package com.universite.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

/**
 * NavigationUtil – Simplifie la navigation entre les vues FXML.
 */
public class NavigationUtil {

    private NavigationUtil() {}

    /**
     * Change la scène actuelle vers un nouveau fichier FXML.
     *
     * @param stage  Le Stage courant
     * @param fxml   Chemin FXML relatif aux resources (ex: "/fxml/auth/Login.fxml")
     * @param titre  Titre de la fenêtre
     */
    public static void naviguerVers(Stage stage, String fxml, String titre) {
        try {
            URL resource = NavigationUtil.class.getResource(fxml);
            if (resource == null) {
                throw new IOException("FXML introuvable: " + fxml);
            }
            Parent root = FXMLLoader.load(resource);
            Scene scene = new Scene(root);

            // Appliquer le CSS du thème courant
            String theme = SessionManager.getThemeCSS();
            String cssPath = "/css/" + theme + ".css";
            if (NavigationUtil.class.getResource(cssPath) != null) {
                scene.getStylesheets().add(NavigationUtil.class.getResource(cssPath).toExternalForm());
            } else {
                scene.getStylesheets().add(NavigationUtil.class.getResource("/css/style.css").toExternalForm());
            }

            // Appliquer le zoom global (loupe)
            appliquerZoom(root);

            // Raccourcis clavier zoom : Ctrl++ / Ctrl+- / Ctrl+0
            scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if (!e.isControlDown()) return;
                if (e.getCode() == KeyCode.PLUS || e.getCode() == KeyCode.ADD || (e.getCode() == KeyCode.EQUALS && e.isShiftDown())) {
                    SessionManager.setZoomFactor(SessionManager.getZoomFactor() + 0.1);
                    appliquerZoom(root);
                    e.consume();
                } else if (e.getCode() == KeyCode.MINUS || e.getCode() == KeyCode.SUBTRACT) {
                    SessionManager.setZoomFactor(SessionManager.getZoomFactor() - 0.1);
                    appliquerZoom(root);
                    e.consume();
                } else if (e.getCode() == KeyCode.DIGIT0 || e.getCode() == KeyCode.NUMPAD0) {
                    SessionManager.setZoomFactor(1.0);
                    appliquerZoom(root);
                    e.consume();
                }
            });

            stage.setScene(scene);
            stage.setTitle(titre);
            stage.setMaximized(true);
            stage.show();
        } catch (Exception e) {
            System.err.println("Erreur de navigation vers " + fxml + " : " + e.getMessage());
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Navigation");
            alert.setHeaderText("Impossible d'ouvrir la page");
            alert.setContentText(String.valueOf(e.getMessage()));
            alert.showAndWait();
        }
    }

    private static void appliquerZoom(Parent root) {
        double z = SessionManager.getZoomFactor();
        root.setScaleX(z);
        root.setScaleY(z);
    }

    /**
     * Raccourci pour récupérer le Stage depuis n'importe quel nœud JavaFX.
     */
    public static Stage getStage(javafx.scene.Node node) {
        return (Stage) node.getScene().getWindow();
    }
}
