package io.coinswap.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsoleController {
    private static final Logger log = LoggerFactory.getLogger(ConsoleController.class);

    public void log(String s) {
        log.info(s);
    }

    public void error(String s) {
        log.error(s);
    }
}
