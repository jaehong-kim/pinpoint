package com.pinpoint.test.plugin;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

/**
 * @author Woonduk Kang(emeroad)
 */
public class Vertx4VerticalMain {

    public static void main(String[] args) {
//        Launcher launcher = new Launcher(args);
        VertxOptions vertxOptions = new VertxOptions().setBlockedThreadCheckInterval(200000000);
        Vertx vertx = Vertx.vertx(vertxOptions);
        vertx.deployVerticle(new Vertx4PluginTestStarter());
    }
}
