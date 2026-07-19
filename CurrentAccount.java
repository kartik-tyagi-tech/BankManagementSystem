// Inheritance — CurrentAccount extends Account
public class CurrentAccount extends Account {

    private double overdraftLimit;

    public CurrentAccount(String accountNumber, String accountHolder, double initialBalance, double overdraftLimit) {
        super(accountNumber, accountHolder, initialBalance);
        this.overdraftLimit = overdraftLimit;
    }

    // Polymorphism — implementing abstract method
    @Override
    public String getAccountType() {
        return "CURRENT";
    }

    // Polymorphism — overriding withdraw with overdraft logic
    @Override
    public void withdraw(double amount) throws InsufficientFundsException, IllegalArgumentException {
        if (amount <= 0) throw new IllegalArgumentException("Withdrawal amount must be positive.");
        if (amount > getBalance() + overdraftLimit) {
            throw new InsufficientFundsException("Exceeds overdraft limit! Max available: Rs." + (getBalance() + overdraftLimit));
        }
        setBalance(getBalance() - amount);
        getTransactionHistory().add("Withdrawn: Rs." + amount + " | Balance: Rs." + getBalance());
    }

    public double getOverdraftLimit() { return overdraftLimit; }

    @Override
    public String toFileString() {
        return super.toFileString() + "," + overdraftLimit;
    }
}
