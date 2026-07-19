// 5th class — Encapsulation
public class Customer {

    // Private fields — Encapsulation
    private String customerId;
    private String name;
    private String phone;

    public Customer(String customerId, String name, String phone) {
        this.customerId = customerId;
        this.name = name;
        this.phone = phone;
    }

    // Getters — Encapsulation
    public String getCustomerId() { return customerId; }
    public String getName() { return name; }
    public String getPhone() { return phone; }

    @Override
    public String toString() {
        return "Customer ID: " + customerId + " | Name: " + name + " | Phone: " + phone;
    }
}
