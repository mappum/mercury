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
        window.setMember("console", new ConsoleController());
        window.setMember("clipboard", new ClipboardController());
        window.setMember("desktop", new DesktopController());

        context = (JSObject) window.getMember("coinswap");
        app = new Model((JSObject) context.getMember("app"));

        e = new Executor() {
            @Override
            public void execute(Runnable r) {
                Platform.runLater(r);
            }
        };
    }

    public JSObject eval(String js) {
        return (JSObject) engine.executeScript(js);
    }

    public JSObject getContext() { return context; }

    public void callFunction(JSObject function, Object[] args) {
        // hack to be able to call function:
        // create a wrapper object, set the function as a property, then use wrapper.call
        JSObject wrapper = eval("new Object()");
        wrapper.setMember("f", function);
        wrapper.call("f", args);
    }
}
