package io.coinswap.client;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

public class ClipboardController {
    final Clipboard clipboard;
    final ClipboardContent content;

    public ClipboardController() {
        clipboard = Clipboard.getSystemClipboard();
        content = new ClipboardContent();
    }

    public void set(String s) {
        content.clear();
        content.putString(s);
        clipboard.setContent(content);
    }

    public String get() {
        return clipboard.getString();
    }
}
