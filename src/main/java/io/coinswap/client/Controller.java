package io.coinswap.client;

import javafx.application.Platform;
import javafx.scene.web.WebEngine;

import java.util.concurrent.Executor;

public class Controller {
    public Executor e;

    protected WebEngine engine;
    protected int index = 0;

    public Controller(WebEngine engine) {
        this.engine = engine;

        e = new Executor() {
            @Override public void execute(Runnable runnable) {
                Platform.runLater(runnable);
            }
        };
    }

    public Emitter createEmitter() {
        return createEmitter(index++ + "");
    }
    public Emitter createEmitter(String id) {
        return new Emitter(id);
    }

    class Emitter {
        protected String id;

        public Emitter(String id) {
            this.id = id;
        }

        public void emit(String event, String data) {
            engine.executeScript("window.controller.emit('" + id + "', '" + event + "', '" + data + "');");
        }
    }
}
