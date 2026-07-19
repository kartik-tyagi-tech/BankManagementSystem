import java.util.Scanner;

// Main class — Entry point of the application
public class BankApp {

    private static Bank bank = new Bank();
    private static Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("       BANK MANAGEMENT SYSTEM          ");
        System.out.println("========================================");

        while (true) {
            showMenu();
            int choice = getIntInput("Enter your choice: ");
            switch (choice) {
                case 1: createAccount();        break;
                case 2: depositMoney();         break;
                case 3: withdrawMoney();        break;
                case 4: transferFunds();        break;
                case 5: checkBalance();         break;
                case 6: viewTransactionHistory(); break;
                case 7: bank.displayAllAccounts(); break;
                case 8:
                    System.out.println("Thank you for using Bank Management System!");
                    System.exit(0);
                default:
                    System.out.println("Invalid choice. Please try again.");
            }
        }
    }

    private static void showMenu() {
        System.out.println("\n---------- MENU ----------");
        System.out.println("1. Create Account");
        System.out.println("2. Deposit Money");
        System.out.println("3. Withdraw Money");
        System.out.println("4. Fund Transfer");
        System.out.println("5. Check Balance");
        System.out.println("6. Transaction History");
        System.out.println("7. View All Accounts");
        System.out.println("8. Exit");
        System.out.println("--------------------------");
    }

    private static void createAccount() {
        System.out.println("\n1. Savings Account");
        System.out.println("2. Current Account");
        int type = getIntInput("Choose account type: ");
        System.out.print("Enter account holder name: ");
        String name = scanner.nextLine();
        double balance = getDoubleInput("Enter initial deposit (Rs.): ");

        try {
            Account acc;
            if (type == 1) {
                acc = bank.createSavingsAccount(name, balance);
            } else {
                double overdraft = getDoubleInput("Enter overdraft limit (Rs.): ");
                acc = bank.createCurrentAccount(name, balance, overdraft);
            }
            System.out.println("\nAccount created successfully!");
            System.out.println(acc);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void depositMoney() {
        System.out.print("\nEnter account number: ");
        String accNo = scanner.nextLine();
        double amount = getDoubleInput("Enter deposit amount (Rs.): ");
        try {
            bank.deposit(accNo, amount);
            System.out.println("Deposit successful!");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void withdrawMoney() {
        System.out.print("\nEnter account number: ");
        String accNo = scanner.nextLine();
        double amount = getDoubleInput("Enter withdrawal amount (Rs.): ");
        try {
            bank.withdraw(accNo, amount);
            System.out.println("Withdrawal successful!");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void transferFunds() {
        System.out.print("\nEnter SOURCE account number: ");
        String fromAcc = scanner.nextLine();
        System.out.print("Enter DESTINATION account number: ");
        String toAcc = scanner.nextLine();
        double amount = getDoubleInput("Enter transfer amount (Rs.): ");
        try {
            bank.transfer(fromAcc, toAcc, amount);
            System.out.println("Transfer of Rs." + amount + " successful!");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void checkBalance() {
        System.out.print("\nEnter account number: ");
        String accNo = scanner.nextLine();
        Account acc = bank.getAccount(accNo);
        if (acc != null) {
            System.out.println("Account: " + acc.getAccountHolder());
            System.out.println("Balance: Rs." + acc.getBalance());
        } else {
            System.out.println("Account not found!");
        }
    }

    private static void viewTransactionHistory() {
        System.out.print("\nEnter account number: ");
        String accNo = scanner.nextLine();
        Account acc = bank.getAccount(accNo);
        if (acc != null) {
            System.out.println("\n--- Transaction History for " + accNo + " ---");
            for (String txn : acc.getTransactionHistory()) {
                System.out.println("  > " + txn);
            }
        } else {
            System.out.println("Account not found!");
        }
    }

    // Input helpers with validation
    private static int getIntInput(String prompt) {
        System.out.print(prompt);
        while (!scanner.hasNextInt()) {
            System.out.print("Invalid! " + prompt);
            scanner.next();
        }
        int val = scanner.nextInt();
        scanner.nextLine();
        return val;
    }

    private static double getDoubleInput(String prompt) {
        System.out.print(prompt);
        while (!scanner.hasNextDouble()) {
            System.out.print("Invalid! " + prompt);
            scanner.next();
        }
        double val = scanner.nextDouble();
        scanner.nextLine();
        return val;
    }
}
