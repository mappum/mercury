package io.coinswap.client;

import com.google.bitcoin.params.*;

import javafx.application.Application;
import javafx.application.Platform;
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
import javafx.scene.image.Image;
import netscape.javascript.JSObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Main extends Application {
    public static final String APP_NAME = "Coinswap";
    public static final String APP_VERSION = "0.0.1-SNAPSHOT";

    public static final File dataDir = new File("./data");

    private static final int MIN_WIDTH = 740;
    private static final int MIN_HEIGHT = 400;
    private static final int DEFAULT_WIDTH = 800;
    private static final int DEFAULT_HEIGHT = 720;

    private List<Coin> coins;
    private ClientUI ui;
    private Controller controller;

    @Override
    public void start(Stage mainWindow) {
        ui = new ClientUI();
        ui.engine.getLoadWorker().stateProperty().addListener((ov, oldState, state) -> {
            controller = new Controller(ui.engine);

            // TODO: load pairs from exchange server
            coins = new ArrayList<Coin>();
            coins.add(new Coin(controller, MainNetParams.get(), dataDir,
                    "Bitcoin", "BTC", "<i class=\"fa fa-bitcoin\"></i>",
                    new String[] { "LTC", "DOGE", "BTCt", "LTCt" }));
            coins.add(new Coin(controller, LitecoinMainNetParams.get(), dataDir,
                    "Litecoin", "LTC", "&#321;",
                    new String[] { "BTC", "DOGE", "LTCt" }));
            coins.add(new Coin(controller, DogecoinMainNetParams.get(), dataDir,
                    "Dogecoin", "DOGE", "&#272;",
                    new String[] { "BTC", "LTC" }));
            coins.add(new Coin(controller, TestNet3Params.get(), dataDir,
                    "Bitcoin Testnet", "BTCt", "<i class=\"fa fa-bitcoin\"></i>",
                    new String[] { "BTC", "LTCt" }));
            coins.add(new Coin(controller, LitecoinTestNetParams.get(), dataDir,
                    "Litecoin Testnet", "LTCt", "&#321;",
                    new String[] { "BTC", "LTC", "BTCt" }));

            // TODO: sort coins by volume

            // insert coin objects into JS-side collection
            JSObject coinCollection = (JSObject) controller.app.get("coins");
            for(Coin coin : coins) {
                coin.start();
                coinCollection.call("add", new Object[]{ coin.model.object });
            }

            mainWindow.setTitle(APP_NAME);
            mainWindow.setScene(new Scene(ui, DEFAULT_WIDTH, DEFAULT_HEIGHT));
            mainWindow.setMinWidth(MIN_WIDTH);
            mainWindow.setMinHeight(MIN_HEIGHT);

            mainWindow.getIcons().add(new Image(getClass().getResourceAsStream("/images/icon128.png")));
            mainWindow.getIcons().add(new Image(getClass().getResourceAsStream("/images/icon64.png")));
            mainWindow.getIcons().add(new Image(getClass().getResourceAsStream("/images/icon32.png")));

            mainWindow.show();
        });
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        for(Coin coin : coins) coin.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

class ClientUI extends Region {
    WebView browser;
    WebEngine engine;

    public ClientUI() {
        Platform.setImplicitExit(true);

        browser =  new WebView();
        engine = browser.getEngine();

        browser.setContextMenuEnabled(false);
        getStyleClass().add("browser");
        engine.load(getClass().getResource("/index.html").toExternalForm());
        engine.setUserStyleSheetLocation(getClass().getResource("/css/browser.css").toExternalForm());
        browser.setFontSmoothingType(FontSmoothingType.GRAY);

        // load fonts
        // TODO: put this somewhere else
        Font.loadFont(getClass().getResource("/fonts/lato-regular.woff").toExternalForm(), 10);
        Font.loadFont(getClass().getResource("/fonts/lato-bold.woff").toExternalForm(), 10);
        Font.loadFont(getClass().getResource("/fonts/lato-black.woff").toExternalForm(), 10);
        Font.loadFont(getClass().getResource("/fonts/lato-italic.woff").toExternalForm(), 10);
        Font.loadFont(getClass().getResource("/fonts/fontawesome-webfont.eot").toExternalForm(), 10);

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