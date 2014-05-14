package io.coinswap.client;

import javafx.application.Platform;
import javafx.scene.web.WebEngine;
import netscape.javascript.JSObject;

import java.util.concurrent.Executor;

public class Controller {

    public Executor e;

    protected WebEngine engine;
    protected int index = 0;
    protected JSObject context;
    protected Model app;

    public Controller(WebEngine engine) {
        this.engine = engine;

        JSObject window = (JSObject) engine.executeScript("window");
        window.setMember("console", new Console());

        context = (JSObject) window.getMember("coinswap");
        app = new Model((JSObject) context.getMember("app"));

        e = Platform::runLater;
    }

    public JSObject eval(String js) {
        return (JSObject) engine.executeScript(js);
    }

    public JSObject getContext() { return context; }
}
