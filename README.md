# 🏦 Bank Management System

A console-based **Bank Management System** built in Java demonstrating core Object-Oriented Programming principles including Encapsulation, Inheritance, Polymorphism, and Abstraction. The system supports real-world banking operations with persistent data storage using File I/O.

---

## 📋 Table of Contents
- [Features](#features)
- [Project Structure](#project-structure)
- [OOP Concepts Used](#oop-concepts-used)
- [Technologies Used](#technologies-used)
- [How to Run](#how-to-run)
- [Usage](#usage)
- [Sample Output](#sample-output)

---

## ✨ Features

- ✅ Create **Savings** and **Current** bank accounts
- ✅ **Deposit** money into any account
- ✅ **Withdraw** money with insufficient funds protection
- ✅ **Fund Transfer** between two accounts
- ✅ **Check Balance** instantly
- ✅ View complete **Transaction History**
- ✅ **Overdraft support** for Current accounts
- ✅ **Persistent storage** — data saved across sessions using File I/O
- ✅ **Input validation** — handles invalid user inputs gracefully

---

## 📁 Project Structure

```
BankManagementSystem/
│
├── BankApp.java                  # Main entry point — console menu
├── Bank.java                     # Core logic — HashMap + File I/O
├── Account.java                  # Abstract base class — Encapsulation + Polymorphism
├── SavingsAccount.java           # Extends Account — Inheritance
├── CurrentAccount.java           # Extends Account — Polymorphism (overdraft)
├── Customer.java                 # Customer entity — Encapsulation
├── InsufficientFundsException.java  # Custom exception — Exception Handling
└── accounts.txt                  # Auto-generated data file (File I/O)
```

---

## 🧠 OOP Concepts Used

### 1. Encapsulation
All fields in `Account` and `Customer` are `private` and accessed only through public getter methods.
```java
private String accountNumber;
private double balance;

public String getAccountNumber() { return accountNumber; }
public double getBalance() { return balance; }
```

### 2. Inheritance
`SavingsAccount` and `CurrentAccount` extend `Account`, inheriting all common banking operations.
```java
public class SavingsAccount extends Account { ... }
public class CurrentAccount extends Account { ... }
```

### 3. Polymorphism
`CurrentAccount` overrides the `withdraw()` method to support overdraft transactions.
```java
@Override
public void withdraw(double amount) throws InsufficientFundsException {
    // Allows withdrawal up to balance + overdraftLimit
}
```

### 4. Abstraction
`Account` is an abstract class — cannot be instantiated directly. Forces subclasses to implement `getAccountType()`.
```java
public abstract String getAccountType();
```

### 5. Exception Handling
Custom `InsufficientFundsException` thrown when withdrawal exceeds available balance.
```java
throw new InsufficientFundsException("Insufficient funds! Available: Rs." + balance);
```

### 6. Collections Framework
- `HashMap<String, Account>` — O(1) average-case account lookup by account number
- `ArrayList<String>` — stores complete transaction history per account

### 7. File I/O
`BufferedWriter` and `BufferedReader` used to save and load account data persistently across sessions.

---

## 🛠️ Technologies Used

| Technology | Purpose |
|---|---|
| **Java** | Core programming language |
| **OOP** | Encapsulation, Inheritance, Polymorphism, Abstraction |
| **Collections Framework** | HashMap, ArrayList |
| **File I/O** | BufferedWriter, BufferedReader, FileWriter, FileReader |
| **Exception Handling** | Custom exception, try-catch blocks |

---

## ▶️ How to Run

### Prerequisites
- Java JDK 8 or above installed
- Any IDE (IntelliJ IDEA / Eclipse / VS Code) or terminal

### Steps

**1. Clone the repository**
```bash
git clone https://github.com/your-username/BankManagementSystem.git
cd BankManagementSystem
```

**2. Compile all Java files**
```bash
javac *.java
```

**3. Run the application**
```bash
java BankApp
```

---

## 💻 Usage

```
========================================
       BANK MANAGEMENT SYSTEM
========================================

---------- MENU ----------
1. Create Account
2. Deposit Money
3. Withdraw Money
4. Fund Transfer
5. Check Balance
6. Transaction History
7. View All Accounts
8. Exit
--------------------------
Enter your choice:
```

### Creating a Savings Account
```
Choose account type: 1
Enter account holder name: Kartik
Enter initial deposit (Rs.): 5000
Account created successfully!
[SAVINGS] Acc No: ACC1001 | Holder: Kartik | Balance: Rs.5000.0
```

### Withdrawing Money
```
Enter account number: ACC1001
Enter withdrawal amount (Rs.): 10000
Error: Insufficient funds! Available: Rs.5000.0
```

### Fund Transfer
```
Enter SOURCE account number: ACC1001
Enter DESTINATION account number: ACC1002
Enter transfer amount (Rs.): 2000
Transfer of Rs.2000.0 successful!
```

---

## 📊 Sample Output

### Transaction History
```
--- Transaction History for ACC1001 ---
  > Account created with balance: Rs.5000.0
  > Deposited: Rs.2000.0 | Balance: Rs.7000.0
  > Withdrawn: Rs.1000.0 | Balance: Rs.6000.0
  > Withdrawn: Rs.2000.0 | Balance: Rs.4000.0
```

### Data saved in accounts.txt
```
counter:1002
SAVINGS,ACC1001,Kartik,4000.0
CURRENT,ACC1002,Rahul,3000.0,2000.0
```

---

## 🌐 Web UI

```bash
javac *.java
java BankServer
```
Open http://localhost:8080. The web UI uses the same `Bank` class and `accounts.txt` as the console app.

---

## 🤖 Generative AI Banking Assistant

The **Assistant** page lets staff ask questions in plain language ("which accounts have less than ₹5,000?") and prepare transactions ("transfer ₹2,000 from KTB001 to KTB002").

**How it works**
- The browser sends the message to `POST /api/assistant` on `BankServer`. The API key stays on the server and never reaches the browser.
- `AssistantService` calls an OpenAI-compatible chat-completions REST API with **tool (function) calling**. The model gets four tools: `list_accounts`, `get_account`, `get_statement`, `propose_transaction`. It must call these to get figures, so balances come from `Bank`, not from the model's guesswork.
- `propose_transaction` **does not move money**. It returns a confirmation card to the UI, and the transaction is only posted through the normal `/api/deposit`, `/api/withdraw` or `/api/transfer` routes when a person presses **Confirm**.
- `Json.java` is a small JSON reader/writer, so the project still needs no external libraries (works on Java 8+).

**Setup (free)**
1. Get a free API key from [Groq](https://console.groq.com/keys) (no card needed).
2. Copy `ai.properties.example` to `ai.properties` and paste the key into `AI_API_KEY`.
3. Restart `java BankServer`. The console prints which provider and model the assistant is using.

`ai.properties` is in `.gitignore`, so the key is never committed. Google Gemini, OpenRouter, or a local [Ollama](https://ollama.com) model (fully offline, no key) also work — see the commented options in `ai.properties.example`.

---

## 👨‍💻 Author

**Kartik Tyagi**  
MCA Student — VIT Vellore  
[LinkedIn](https://linkedin.com/in/your-profile) | [GitHub](https://github.com/your-username) | [LeetCode](https://leetcode.com/your-profile)
