import java.io.IOException;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

/** Optional JavaFX desktop window for the browser-based calendar. */
public class BabyCalendarFxApp extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        Main.startServer();

        WebView webView = new WebView();
        webView.getEngine().load("http://localhost:" + Main.getPort());

        Button refreshButton = new Button("Թարմացնել");
        refreshButton.setOnAction(event -> webView.getEngine().reload());
        Label address = new Label("http://localhost:" + Main.getPort());

        BorderPane layout = new BorderPane(webView);
        layout.setTop(new ToolBar(refreshButton, address));
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
}
