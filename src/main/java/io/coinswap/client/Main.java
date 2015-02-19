package io.coinswap.client;

import com.sun.javafx.menu.MenuBase;
import com.sun.javafx.scene.control.GlobalMenuAdapter;
import com.sun.javafx.tk.Toolkit;
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
import javafx.scene.control.Menu;
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

import java.io.File;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main extends Application {
    private static Logger log;

    public static final String APP_NAME = "Mercury Wallet";
    public static final String APP_VERSION = "0.0.1-SNAPSHOT";

    public static final int CLIENT_VERSION = 0;

    private static final int MIN_WIDTH = 740;
    private static final int MIN_HEIGHT = 400;
    private static final int DEFAULT_WIDTH = 1200;
    private static final int DEFAULT_HEIGHT = 780;

    private ClientUI ui;
    private Controller controller;
    private ArrayList<Currency> currencies;
    private SwapCollection swaps;
    private Map<String, CoinModel> models;

    @Override
    public void start(Stage mainWindow) {
        File dataDirectory = getDataDirectory();
        String logPath = new File(dataDirectory, "logs/debug.log").getAbsolutePath();
        System.setProperty("logs.file", logPath);
        log = LoggerFactory.getLogger(Main.class);

        ui = new ClientUI();
        ui.engine.getLoadWorker().stateProperty().addListener(new ChangeListener<Worker.State>() {
            @Override
            public void changed(ObservableValue<? extends Worker.State> observableValue, Worker.State state, Worker.State state2) {

                controller = new Controller(ui.engine);
                currencies = Coins.get(dataDirectory);
                models = new HashMap<String, CoinModel>();
                String[] args = new String[getParameters().getRaw().size()];
                getParameters().getRaw().toArray(args);
                Map<String, List<InetSocketAddress>> connectPeers = getConnectPeers(args, currencies);

                File swapCollectionFile = new File(dataDirectory, "swaps");
                swaps = SwapCollection.load(swapCollectionFile);
                if(swaps == null) swaps = new SwapCollection(swapCollectionFile);

                // for each Coin, create JS-side model and insert into JS-side collection
                final JSObject coinCollection = (JSObject) controller.app.get("coins");
                for(Currency currency : currencies) {
                    final Currency c = currency;
                    currency.getSetupFuture().addListener(new Runnable() {
                        @Override
                        public void run() {
                            CoinModel model = new CoinModel(controller, c, swaps);
                            models.put(c.id, model);
                            coinCollection.call("add", new Object[]{ model.object });
                        }
                    }, controller.e);

                    List<InetSocketAddress> peers = connectPeers.get(c.getId().toLowerCase());
                    if(peers != null) {
                        for(InetSocketAddress peer : peers) {
                            c.getWallet().peerGroup().connectTo(peer);
                        }
                    }

                    currency.start();
                }

                TradeClient tradeClient = new TradeClient(currencies, swaps);
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
                    ex.printStackTrace();
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

    public static Map<String, List<InetSocketAddress>> getConnectPeers(String[] args, List<Currency> currencies) {
        Map<String, List<InetSocketAddress>> output = new HashMap<String, List<InetSocketAddress>>();
        for(String s : args) {
            s = s.toLowerCase();
            if(!s.startsWith("-connect-")) continue;

            String currency = s.substring(9, s.indexOf("=")),
                    address = s.substring(s.indexOf("=")+1),
                    host;
            int port = 0;

            if(address.contains(":")) {
                host = address.substring(0, address.indexOf(":"));
                port = Integer.parseInt(address.substring(address.indexOf(":")+1));
            } else {
                host = address;
                for(Currency c : currencies) {
                    if(c.getId().toLowerCase().equals(currency)) {
                        port = c.getParams().getPort();
                        break;
                    }
                }
            }

            if(output.get(currency) == null) {
                output.put(currency, new ArrayList<InetSocketAddress>(1));
            }
            output.get(currency).add(new InetSocketAddress(host, port));
        }
        return output;
    }
}

class ClientUI extends Region {
    WebView browser;
    WebEngine engine;

    private static final String[] FONTS = new String[]{
            "/fonts/lato-regular.woff",
            "/fonts/lato-bold.woff",
            "/fonts/lato-black.woff",
            "/fonts/lato-italic.woff",
            "/fonts/fontawesome-webfont.woff",
    };

    public ClientUI() {
        Platform.setImplicitExit(true);

        browser =  new WebView();
        engine = browser.getEngine();

        browser.setContextMenuEnabled(false);
        getStyleClass().add("browser");
        engine.load(getClass().getResource("/index.html").toExternalForm());
        engine.setUserStyleSheetLocation(getClass().getResource("/css/browser.css").toExternalForm());
        browser.setFontSmoothingType(FontSmoothingType.GRAY);

        loadFonts();

        getChildren().add(browser);
    }

    private void loadFonts() {
        for(String font : FONTS) {
            Font.loadFont(getClass().getResource(font).toExternalForm(), 10);
        }
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        layoutInArea(browser, 0, 0, w, h, 0, HPos.CENTER, VPos.CENTER);
    }
}