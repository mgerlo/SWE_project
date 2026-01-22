package com.splitmanager.cli;

import java.math.BigDecimal;
import java.util.Scanner;

/**
 * Utility class for handling user input in the CLI.
 * Provides methods to read different types of input with validation.
 */
public class InputHandler {

    private final Scanner scanner;

    /**
     * Creates a new InputHandler with a Scanner for System.in
     */
    public InputHandler() {
        this.scanner = new Scanner(System.in);
    }

    /**
     * Reads a string input from the user.
     *
     * @param prompt the message to display to the user
     * @return the string entered by the user
     */
    public String readString(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    /**
     * Reads a non-empty string from the user.
     * Keeps asking until a non-empty string is provided.
     *
     * @param prompt the message to display to the user
     * @return a non-empty string
     */
    public String readNonEmptyString(String prompt) {
        String input;
        do {
            input = readString(prompt);
            if (input.isEmpty()) {
                System.out.println("Input cannot be empty. Please try again.");
            }
        } while (input.isEmpty());
        return input;
    }

    /**
     * Reads an integer from the user.
     * Keeps asking until a valid integer is provided.
     *
     * @param prompt the message to display to the user
     * @return the integer entered by the user
     */
    public int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                String input = scanner.nextLine().trim();
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Invalid number. Please enter a valid integer.");
            }
        }
    }

    /**
     * Reads an integer within a specified range.
     * Keeps asking until a valid integer in the range is provided.
     *
     * @param prompt the message to display to the user
     * @param min minimum value (inclusive)
     * @param max maximum value (inclusive)
     * @return the integer entered by the user
     */
    public int readInt(String prompt, int min, int max) {
        while (true) {
            int value = readInt(prompt);
            if (value >= min && value <= max) {
                return value;
            }
            System.out.println("Please enter a number between " + min + " and " + max + ".");
        }
    }

    /**
     * Reads a Long from the user.
     * Keeps asking until a valid Long is provided.
     *
     * @param prompt the message to display to the user
     * @return the Long entered by the user
     */
    public Long readLong(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                String input = scanner.nextLine().trim();
                return Long.parseLong(input);
            } catch (NumberFormatException e) {
                System.out.println("Invalid number. Please enter a valid number.");
            }
        }
    }

    /**
     * Reads a BigDecimal (for monetary amounts) from the user.
     * Keeps asking until a valid positive amount is provided.
     *
     * @param prompt the message to display to the user
     * @return the BigDecimal entered by the user
     */
    public BigDecimal readAmount(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                String input = scanner.nextLine().trim();
                BigDecimal amount = new BigDecimal(input);
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    System.out.println("Amount must be positive.");
                    continue;
                }
                return amount;
            } catch (NumberFormatException e) {
                System.out.println("Invalid amount. Please enter a valid number (e.g., 25.50).");
            }
        }
    }

    /**
     * Reads a yes/no confirmation from the user.
     *
     * @param prompt the message to display to the user
     * @return true if user confirms (y/yes), false otherwise
     */
    public boolean readConfirmation(String prompt) {
        String input = readString(prompt + " (y/n): ").toLowerCase();
        return input.equals("y") || input.equals("yes");
    }

    /**
     * Displays a message and waits for user to press Enter to continue.
     */
    public void waitForEnter() {
        System.out.println("\nPress Enter to continue...");
        scanner.nextLine();
    }

    /**
     * Closes the scanner.
     * Should be called when the application is closing.
     */
    public void close() {
        scanner.close();
    }

    /**
     * Prints a header with a title.
     *
     * @param title the title to display
     */
    public void printHeader(String title) {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("  " + title);
        System.out.println("=".repeat(50));
    }

    /**
     * Prints a separator line.
     */
    public void printSeparator() {
        System.out.println("-".repeat(50));
    }

    /**
     * Prints a success message.
     *
     * @param message the success message
     */
    public void printSuccess(String message) {
        System.out.println("[SUCCESS] " + message);
    }

    /**
     * Prints an error message.
     *
     * @param message the error message
     */
    public void printError(String message) {
        System.out.println("[ERROR] " + message);
    }

    /**
     * Prints an info message.
     *
     * @param message the info message
     */
    public void printInfo(String message) {
        System.out.println("[INFO] " + message);
    }
}
