package io.coinswap.client;

import io.coinswap.Coins;
import io.coinswap.market.TradeClient;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.event.EventHandler;
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
import javafx.stage.WindowEvent;
import netscape.javascript.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Main extends Application {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static final String APP_NAME = "Mercury Wallet";
    public static final String APP_VERSION = "0.0.1-SNAPSHOT";

    private static final int MIN_WIDTH = 740;
    private static final int MIN_HEIGHT = 400;
    private static final int DEFAULT_WIDTH = 1024;
    private static final int DEFAULT_HEIGHT = 768;

    private ClientUI ui;
    private Controller controller;
    private ArrayList<Currency> currencies;
    private Map<String, CoinModel> models;

    @Override
    public void start(Stage mainWindow) {
        ui = new ClientUI();
        ui.engine.getLoadWorker().stateProperty().addListener(new ChangeListener<Worker.State>() {
            @Override
            public void changed(ObservableValue<? extends Worker.State> observableValue, Worker.State state, Worker.State state2) {
                controller = new Controller(ui.engine);
                currencies = Coins.get(getDataDirectory());
                models = new HashMap<String, CoinModel>();

                // for each Coin, create JS-side model and insert into JS-side collection
                final JSObject coinCollection = (JSObject) controller.app.get("coins");
                for(Currency currency : currencies) {
                    final Currency c = currency;
                    currency.getSetupFuture().addListener(new Runnable() {
                        @Override
                        public void run() {
                            CoinModel model = new CoinModel(controller, c);
                            models.put(c.id, model);
                            coinCollection.call("add", new Object[]{ model.object });
                        }
                    }, controller.e);

                    currency.start();
                }

                TradeClient tradeClient = new TradeClient(currencies);
                tradeClient.start();
                controller.context.setMember("trade", new TradeController(controller, tradeClient));
                controller.app.trigger("initialized");
            }
        });

        mainWindow.setTitle(APP_NAME);
        mainWindow.setScene(new Scene(ui, DEFAULT_WIDTH, DEFAULT_HEIGHT));
        mainWindow.setMinWidth(MIN_WIDTH);
        mainWindow.setMinHeight(MIN_HEIGHT);

        mainWindow.getIcons().add(new Image(getClass().getResourceAsStream("/images/icon128.png")));
        mainWindow.getIcons().add(new Image(getClass().getResourceAsStream("/images/icon64.png")));
        mainWindow.getIcons().add(new Image(getClass().getResourceAsStream("/images/icon32.png")));

        mainWindow.show();

        mainWindow.setOnCloseRequest(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent windowEvent) {
                try {
                    stop();
                } catch(Exception ex) {
                } finally {
                    System.exit(0);
                }
            }
        });
    }

    @Override
    public void stop() throws Exception {
        for(Currency currency : currencies) currency.stop();
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private File getDataDirectory() {
        String envVar = System.getenv("DIR");
        if(envVar != null) return new File(envVar);

        String homePath = System.getProperty("user.home");
        return new File(homePath + "/.mercury");
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
        Font.loadFont(getClass().getResource("/fonts/fontawesome-webfont.woff").toExternalForm(), 10);

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