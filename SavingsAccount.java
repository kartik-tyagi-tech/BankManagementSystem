// Inheritance — SavingsAccount extends Account
public class SavingsAccount extends Account {

    private static final double INTEREST_RATE = 0.04; // 4% interest

    public SavingsAccount(String accountNumber, String accountHolder, double initialBalance) {
        super(accountNumber, accountHolder, initialBalance);
    }

    // Polymorphism — implementing abstract method
    @Override
    public String getAccountType() {
        return "SAVINGS";
    }

    // Extra feature specific to SavingsAccount
    public void applyInterest() {
        double interest = getBalance() * INTEREST_RATE;
        try {
            deposit(interest);
            System.out.println("Interest of Rs." + interest + " applied!");
        } catch (IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
