package com.splitmanager.cli;

/**
 * Main entry point for the SplitManager CLI application.
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("Welcome to SplitManager!");
        System.out.println("Starting application...\n");

        // Create and start the main menu
        CLIMenu menu = new CLIMenu();
        menu.start();

        System.out.println("\nThank you for using SplitManager!");
        System.out.println("Goodbye!");
    }
}
