import java.io.IOException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.concurrent.Worker;

/** Optional JavaFX desktop window for the browser-based calendar. */
public class BabyCalendarFxApp extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        try {
            Main.startServer();
        } catch (IOException exception) {
            showError("Չհաջողվեց գործարկել օրացույցի սերվերը", exception.getMessage());
            Platform.exit();
            return;
        }

        WebView webView = new WebView();
        webView.setContextMenuEnabled(true);
        String appUrl = "http://localhost:" + Main.getPort();
        webView.getEngine().load(appUrl);

        Button refreshButton = new Button("Թարմացնել");
        refreshButton.setOnAction(event -> webView.getEngine().reload());
        Button openBrowserButton = new Button("Բացել բրաուզերում");
        openBrowserButton.setOnAction(event -> openInBrowser(appUrl));
        Label address = new Label(appUrl);
        Label status = new Label("Բեռնվում է…");
        webView.getEngine().getLoadWorker().stateProperty().addListener((observable, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) status.setText("Պատրաստ է");
            else if (newState == Worker.State.FAILED) status.setText("Չհաջողվեց բեռնել էջը");
        });

        BorderPane layout = new BorderPane(webView);
        layout.setTop(new ToolBar(refreshButton, openBrowserButton, address));
        layout.setBottom(status);
        stage.setTitle("Մանկիկի զարգացման օրացույց");
        stage.setMinWidth(800);
        stage.setMinHeight(600);
        stage.setScene(new Scene(layout, 1180, 820));
        stage.show();
    }

    @Override
    public void stop() {
        Main.stopServer();
    }

    public static void main(String[] args) {
        // Keep the browser server's default port free so both launch modes can run together.
        if (System.getProperty("port") == null) {
            System.setProperty("port", "8081");
        }
        launch(args);
    }

    private void openInBrowser(String url) {
        try {
            getHostServices().showDocument(url);
        } catch (Exception exception) {
            showError("Չհաջողվեց բացել բրաուզերը", exception.getMessage());
        }
    }

    private static void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Մանկիկի զարգացման օրացույց");
        alert.setHeaderText(title);
        alert.setContentText(message == null ? "Անհայտ սխալ" : message);
        alert.showAndWait();
    }
}
