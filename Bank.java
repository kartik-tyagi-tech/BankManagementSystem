import java.util.HashMap;
import java.util.Map;
import java.io.*;

// Core Bank class — HashMap (O(1) search) + File I/O
public class Bank {

    // HashMap for O(1) average-case account lookup
    private HashMap<String, Account> accounts;
    private static final String DATA_FILE = "accounts.txt";
    private int accountCounter;

    public Bank() {
        accounts = new HashMap<>();
        accountCounter = 0;
        loadFromFile(); // Load saved data on startup
    }

    // Generate unique account number
    private String generateAccountNumber() {
        return String.format("KTB%03d", ++accountCounter);
    }

    // Create Savings Account
    public Account createSavingsAccount(String holderName, double initialBalance) {
        String accNo = generateAccountNumber();
        SavingsAccount account = new SavingsAccount(accNo, holderName, initialBalance);
        accounts.put(accNo, account); // O(1) insertion into HashMap
        saveToFile();
        return account;
    }

    // Create Current Account
    public Account createCurrentAccount(String holderName, double initialBalance, double overdraftLimit) {
        String accNo = generateAccountNumber();
        CurrentAccount account = new CurrentAccount(accNo, holderName, initialBalance, overdraftLimit);
        accounts.put(accNo, account); // O(1) insertion
        saveToFile();
        return account;
    }

    // Get account — O(1) average-case search using HashMap
    public Account getAccount(String accountNumber) {
        return accounts.get(accountNumber);
    }

    // Deposit
    public void deposit(String accountNumber, double amount) throws Exception {
        Account account = accounts.get(accountNumber); // O(1) lookup
        if (account == null) throw new Exception("Account not found: " + accountNumber);
        account.deposit(amount);
        saveToFile();
    }

    // Withdraw
    public void withdraw(String accountNumber, double amount) throws Exception {
        Account account = accounts.get(accountNumber); // O(1) lookup
        if (account == null) throw new Exception("Account not found: " + accountNumber);
        account.withdraw(amount);
        saveToFile();
    }

    // Fund Transfer between accounts
    public void transfer(String fromAccNo, String toAccNo, double amount) throws Exception {
        Account fromAcc = accounts.get(fromAccNo);
        Account toAcc = accounts.get(toAccNo);
        if (fromAcc == null) throw new Exception("Source account not found!");
        if (toAcc == null) throw new Exception("Destination account not found!");
        fromAcc.withdraw(amount);
        toAcc.deposit(amount);
        saveToFile();
    }

    // Display all accounts
    public void displayAllAccounts() {
        if (accounts.isEmpty()) {
            System.out.println("No accounts found.");
            return;
        }
        for (Map.Entry<String, Account> entry : accounts.entrySet()) {
            System.out.println(entry.getValue());
        }
    }

    // Used by the web UI only — does not change banking rules
    public java.util.Collection<Account> getAllAccounts() {
        return accounts.values();
    }

    // FILE I/O — Save all account data to file (persistent storage)
    private void saveToFile() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(DATA_FILE))) {
            writer.write("counter:" + accountCounter);
            writer.newLine();
            for (Account acc : accounts.values()) {
                writer.write(acc.toFileString());
                writer.newLine();
            }
        } catch (IOException e) {
            System.out.println("Error saving data: " + e.getMessage());
        }
    }

    // FILE I/O — Load account data from file on startup
    private void loadFromFile() {
        File file = new File(DATA_FILE);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(DATA_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("counter:")) {
                    accountCounter = Integer.parseInt(line.split(":")[1]);
                } else {
                    String[] parts = line.split(",");
                    if (parts.length >= 4) {
                        String type = parts[0];
                        String accNo = parts[1];
                        String holder = parts[2];
                        double balance = Double.parseDouble(parts[3]);
                        if (type.equals("SAVINGS")) {
                            accounts.put(accNo, new SavingsAccount(accNo, holder, balance));
                        } else if (type.equals("CURRENT") && parts.length == 5) {
                            double overdraft = Double.parseDouble(parts[4]);
                            accounts.put(accNo, new CurrentAccount(accNo, holder, balance, overdraft));
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Error loading data: " + e.getMessage());
        }
    }
}
