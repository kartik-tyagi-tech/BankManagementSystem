import java.util.ArrayList;

// Abstract class — Encapsulation + Polymorphism
public abstract class Account {

    // Encapsulation — private fields
    private String accountNumber;
    private String accountHolder;
    private double balance;
    private ArrayList<String> transactionHistory; // ArrayList for transaction history

    public Account(String accountNumber, String accountHolder, double initialBalance) {
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
        this.balance = initialBalance;
        this.transactionHistory = new ArrayList<>();
        transactionHistory.add("Account created with balance: Rs." + initialBalance);
    }

    // Encapsulation — Getters
    public String getAccountNumber() { return accountNumber; }
    public String getAccountHolder() { return accountHolder; }
    public double getBalance() { return balance; }
    public ArrayList<String> getTransactionHistory() { return transactionHistory; }

    // Protected setter for subclasses
    protected void setBalance(double balance) { this.balance = balance; }

    // Deposit method
    public void deposit(double amount) throws IllegalArgumentException {
        if (amount <= 0) throw new IllegalArgumentException("Deposit amount must be positive.");
        balance += amount;
        transactionHistory.add("Deposited: Rs." + amount + " | Balance: Rs." + balance);
    }

    // Withdraw method — overridden in subclasses (Polymorphism)
    public void withdraw(double amount) throws InsufficientFundsException, IllegalArgumentException {
        if (amount <= 0) throw new IllegalArgumentException("Withdrawal amount must be positive.");
        if (amount > balance) throw new InsufficientFundsException("Insufficient funds! Available: Rs." + balance);
        balance -= amount;
        transactionHistory.add("Withdrawn: Rs." + amount + " | Balance: Rs." + balance);
    }

    // Abstract method — Polymorphism (each subclass implements differently)
    public abstract String getAccountType();

    // For File I/O — converts account data to saveable string
    public String toFileString() {
        return getAccountType() + "," + accountNumber + "," + accountHolder + "," + balance;
    }

    @Override
    public String toString() {
        return "[" + getAccountType() + "] Acc No: " + accountNumber +
               " | Holder: " + accountHolder + " | Balance: Rs." + balance;
    }
}
