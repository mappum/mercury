package io.coinswap.client;

import javafx.application.Application;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontSmoothingType;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;


public class Main extends Application {
    private Scene scene;
    @Override public void start(Stage stage) {
        stage.setTitle("Coinswap");
        scene = new Scene(new Browser(), 750, 500, Color.web("#666970"));
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args){
        launch(args);
    }
}

class Browser extends Region {
    final WebView browser = new WebView();
    final WebEngine webEngine = browser.getEngine();

    public Browser() {
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

    @Override protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        layoutInArea(browser, 0, 0, w, h, 0, HPos.CENTER, VPos.CENTER);
    }

    @Override protected double computePrefWidth(double height) {
        return 750;
    }

    @Override protected double computePrefHeight(double width) {
        return 500;
    }
}