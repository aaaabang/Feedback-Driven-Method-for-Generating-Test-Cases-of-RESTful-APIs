package org.restct;

public class Main {
    public static void main(String[] args) {
        try {
            Config.parseArgs(args);
        } catch (Exception e) {
            System.err.println("An error occurred: " + e.getMessage());
        }
        RESTCT.loadConfig();
        RESTCT.run();
    }
}