package io.coinswap.client;

import com.google.bitcoin.kits.WalletAppKit;
import com.google.bitcoin.params.MainNetParams;
import javafx.application.Application;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.FontSmoothingType;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.io.File;

public class Main extends Application {
    public static final String APP_NAME = "Coinswap";
    public static final int MIN_WIDTH = 750;
    public static final int MIN_HEIGHT = 500;

    public static WalletAppKit bitcoin;

    private ClientUI ui;
    private Controller controller;

    @Override
    public void start(Stage mainWindow) {
        ui = new ClientUI();
        controller = new Controller();

        // TODO: use correct directory
        // TODO: instantiate appkits for altcoins?
        bitcoin = new WalletAppKit(MainNetParams.get(), new File("."), APP_NAME);

        mainWindow.setTitle(APP_NAME);
        mainWindow.setScene(new Scene(ui, MIN_WIDTH, MIN_HEIGHT));
        mainWindow.setMinWidth(MIN_WIDTH);
        mainWindow.setMinHeight(MIN_HEIGHT);
        mainWindow.show();
    }

    @Override
    public void stop() throws Exception {
        bitcoin.stopAndWait();
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }

    public class ClientUI extends Region {
        final WebView browser = new WebView();
        final WebEngine webEngine = browser.getEngine();

        public ClientUI() {
            browser.setContextMenuEnabled(false);

            getStyleClass().add("browser");
            webEngine.load(getClass().getResource("/index.html").toExternalForm());
            webEngine.setUserStyleSheetLocation(getClass().getResource("/css/browser.css").toExternalForm());
            browser.setFontSmoothingType(FontSmoothingType.GRAY);

            // load fonts
            // TODO: put this somewhere else
            Font.loadFont(getClass().getResource("/fonts/lato/lato-regular.woff").toExternalForm(), 10);
            Font.loadFont(getClass().getResource("/fonts/lato/lato-bold.woff").toExternalForm(), 10);
            Font.loadFont(getClass().getResource("/fonts/lato/lato-black.woff").toExternalForm(), 10);
            Font.loadFont(getClass().getResource("/fonts/lato/lato-italic.woff").toExternalForm(), 10);

            getChildren().add(browser);
        }
        private Node createSpacer() {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            return spacer;
        }

        @Override
        protected void layoutChildren() {
            double w = getWidth();
            double h = getHeight();
            layoutInArea(browser, 0, 0, w, h, 0, HPos.CENTER, VPos.CENTER);
        }
    }
}