package com.saasno.utility.logger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Log {

    private static Logger getLogger() {
        String className = new Exception().getStackTrace()[2].getClassName();
        return LoggerFactory.getLogger(className);
    }

    public static void info(String message) {
        getLogger().info(message);
    }

    public static void info(String message, Object arg) {
        getLogger().info(message, arg);
    }

    public static void info(String message, Object... arguments) {
        getLogger().info(message, arguments);
    }

    public static void warn(String message) {
        getLogger().warn(message);
    }

    public static void warn(String message, Object arg) {
        getLogger().info(message, arg);
    }

    public static void warn(String message, Object... arguments) {
        getLogger().info(message, arguments);
    }

    public static void error(String message) {
        getLogger().error(message);
    }

    public static void error(String message, Object arg) {
        getLogger().info(message, arg);
    }

    public static void error(String message, Object... arguments) {
        getLogger().info(message, arguments);
    }

    public static void debug(String message) {
        getLogger().debug(message);
    }

    public static void debug(String message, Object arg) {
        getLogger().info(message, arg);
    }

    public static void debug(String message, Object... arguments) {
        getLogger().info(message, arguments);
    }

}
