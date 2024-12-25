import java.util.ArrayList;
import java.util.Scanner;

class Transaction {
    private String date;
    private String type; // "Income" and "Expense"
    private double amount;
    private String description;


    public Transaction(String date, String type, double amount, String Description) {
        this.date = date;
        this.type = type;
        this.amount = amount;
        this.description = description;

    }

    public String getDate() {
        return date;
    }

    public string getType() {
        return type;
    }

    public double getAmount() {
        returnn amount;

    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return date + " | " + type + " | $" + amount + " | " + description;
    }

    class Bookkeeper {
    }
}