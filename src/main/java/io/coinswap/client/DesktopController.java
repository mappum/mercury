package io.coinswap.client;

import java.awt.*;
import java.net.URI;

public class DesktopController {
    public void browse(String s) {
        try {
            Desktop.getDesktop().browse(new URI(s));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
