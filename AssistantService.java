import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

// Generative AI banking assistant.
//
// Talks to any OpenAI-compatible chat-completions REST API (Groq, OpenRouter,
// Gemini's OpenAI endpoint, or a local Ollama). The model never sees the raw
// data store: it asks for data through "tools" (function calling), and this
// class answers those calls from the Bank object.
//
// The assistant can only PROPOSE deposits, withdrawals and transfers. The
// proposal goes back to the browser, and nothing is posted until a person
// presses Confirm, which calls the normal /api/deposit, /api/withdraw or
// /api/transfer routes.
public class AssistantService {

    private static final int MAX_TOOL_ROUNDS = 6;
    private static final int MAX_HISTORY = 20;

    private final Bank bank;
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public AssistantService(Bank bank) {
        this.bank = bank;
        Properties file = loadProperties("ai.properties");
        String key = setting("AI_API_KEY", file, null);
        if (key == null) key = setting("GROQ_API_KEY", file, "");
        this.apiKey = key.trim();
        this.baseUrl = stripSlash(setting("AI_BASE_URL", file, "https://api.groq.com/openai/v1"));
        this.model = setting("AI_MODEL", file, "openai/gpt-oss-20b");
    }

    // ---------- configuration ----------

    private static Properties loadProperties(String name) {
        Properties p = new Properties();
        File f = new File(name);
        if (f.isFile()) {
            try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException e) {
                System.out.println("Could not read " + name + ": " + e.getMessage());
            }
        }
        return p;
    }

    // Environment variable wins over ai.properties, which wins over the default.
    private static String setting(String key, Properties file, String fallback) {
        String env = System.getenv(key);
        if (env != null && !env.trim().isEmpty()) return env.trim();
        String prop = file.getProperty(key);
        if (prop != null && !prop.trim().isEmpty()) return prop.trim();
        return fallback;
    }

    private static String stripSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private boolean isLocal() {
        return baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1");
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty() || isLocal();
    }

    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("configured", isConfigured());
        m.put("provider", providerName());
        m.put("model", model);
        return m;
    }

    private String providerName() {
        if (baseUrl.contains("groq.com")) return "Groq";
        if (baseUrl.contains("openrouter.ai")) return "OpenRouter";
        if (baseUrl.contains("generativelanguage.googleapis.com")) return "Google Gemini";
        if (isLocal()) return "Local (Ollama)";
        return baseUrl;
    }

    // ---------- chat ----------

    public Map<String, Object> chat(Object historyJson, String userMessage) throws Exception {
        if (!isConfigured()) {
            throw new Exception("Assistant is not set up. Add your API key to ai.properties (see ai.properties.example) and restart the server.");
        }
        if (userMessage == null || userMessage.trim().isEmpty()) {
            throw new Exception("Message is empty.");
        }

        List<Object> messages = new ArrayList<>();
        messages.add(msg("system", systemPrompt()));
        messages.addAll(cleanHistory(historyJson));
        messages.add(msg("user", clip(userMessage.trim(), 1500)));

        Map<String, Object> proposal = null;

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            Map<String, Object> reply = callModel(messages);
            List<Object> toolCalls = Json.arr(reply.get("tool_calls"));

            if (toolCalls == null || toolCalls.isEmpty()) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("reply", tidy(Json.str(reply, "content")));
                out.put("action", proposal);
                return out;
            }

            // Echo the assistant's tool request back, then answer each call.
            Map<String, Object> assistantTurn = new LinkedHashMap<>();
            assistantTurn.put("role", "assistant");
            assistantTurn.put("content", reply.get("content"));
            assistantTurn.put("tool_calls", toolCalls);
            messages.add(assistantTurn);

            for (Object o : toolCalls) {
                Map<String, Object> call = Json.obj(o);
                Map<String, Object> fn = Json.obj(call.get("function"));
                String name = Json.str(fn, "name");
                Map<String, Object> args;
                try {
                    Object parsed = Json.parse(Optional.ofNullable(Json.str(fn, "arguments")).orElse("{}"));
                    args = parsed instanceof Map ? Json.obj(parsed) : new LinkedHashMap<String, Object>();
                } catch (IllegalArgumentException e) {
                    args = new LinkedHashMap<>();
                }

                Map<String, Object> result;
                synchronized (bank) {
                    result = runTool(name, args);
                }
                if (result.get("proposal") != null) proposal = Json.obj(result.remove("proposal"));

                Map<String, Object> toolTurn = new LinkedHashMap<>();
                toolTurn.put("role", "tool");
                toolTurn.put("tool_call_id", Json.str(call, "id"));
                toolTurn.put("content", Json.write(result));
                messages.add(toolTurn);
            }
        }
        throw new Exception("The assistant took too many steps to answer. Try asking more simply.");
    }

    private static Map<String, Object> msg(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    // Only plain user/assistant text is accepted from the browser.
    private static List<Object> cleanHistory(Object historyJson) {
        List<Object> out = new ArrayList<>();
        List<Object> list = Json.arr(historyJson);
        if (list == null) return out;
        int start = Math.max(0, list.size() - MAX_HISTORY);
        for (int k = start; k < list.size(); k++) {
            Map<String, Object> m = Json.obj(list.get(k));
            String role = Json.str(m, "role");
            String content = Json.str(m, "content");
            if (content == null || !("user".equals(role) || "assistant".equals(role))) continue;
            out.add(msg(role, clip(content, 2000)));
        }
        return out;
    }

    private static String clip(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    // Some local models wrap their reasoning in <think> tags; hide it.
    private static String tidy(String s) {
        if (s == null) return "";
        return s.replaceAll("(?s)<think>.*?</think>", "").trim();
    }

    private String systemPrompt() {
        return "You are the banking assistant inside the staff console of Kartik Tyagi Small Finance Bank. "
            + "Staff ask you about customer accounts and ask you to prepare transactions.\n\n"
            + "Rules:\n"
            + "- Always use the tools to get balances, account details and history. Never guess or invent a figure, name or account number.\n"
            + "- Account numbers look like KTB001. Savings accounts cannot go below zero. Current accounts can go negative up to their overdraft limit.\n"
            + "- To deposit, withdraw or transfer, call propose_transaction. This does NOT move money; it shows the staff member a confirmation card. "
            + "Afterwards tell them to review and press Confirm. Never say a transaction is done.\n"
            + "- If a request is missing the account or amount, ask for it instead of assuming.\n"
            + "- Write amounts in rupees with Indian digit grouping, e.g. ₹1,25,000.00.\n"
            + "- Keep answers short and plain: a sentence or two, or a short list with '- '. No tables, no headings.\n"
            + "- You only handle this bank's accounts and general banking questions. Politely decline anything unrelated.\n"
            + "Today's date: " + new java.text.SimpleDateFormat("d MMMM yyyy").format(new Date()) + ".";
    }

    // ---------- tools ----------

    private static Map<String, Object> tool(String name, String description, Map<String, Object> props, String... required) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        params.put("required", Arrays.asList(required));

        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", name);
        fn.put("description", description);
        fn.put("parameters", params);

        Map<String, Object> t = new LinkedHashMap<>();
        t.put("type", "function");
        t.put("function", fn);
        return t;
    }

    private static Map<String, Object> prop(String type, String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", type);
        p.put("description", description);
        return p;
    }

    private static List<Object> toolDefinitions() {
        List<Object> tools = new ArrayList<>();

        tools.add(tool("list_accounts",
            "List every account with number, holder, type, balance and overdraft limit.",
            new LinkedHashMap<String, Object>()));

        Map<String, Object> accOnly = new LinkedHashMap<>();
        accOnly.put("account_number", prop("string", "Account number, e.g. KTB001"));

        tools.add(tool("get_account",
            "Get one account's holder, type, balance, overdraft limit and amount available to withdraw.",
            accOnly, "account_number"));

        tools.add(tool("get_statement",
            "Get the transaction history recorded for an account in the current server session.",
            accOnly, "account_number"));

        Map<String, Object> tx = new LinkedHashMap<>();
        Map<String, Object> action = prop("string", "Kind of transaction");
        action.put("enum", Arrays.asList("deposit", "withdraw", "transfer"));
        tx.put("action", action);
        tx.put("account_number", prop("string", "Account to deposit to / withdraw from / transfer from"));
        tx.put("to_account_number", prop("string", "Destination account, only for transfers"));
        tx.put("amount", prop("number", "Amount in rupees, greater than zero"));

        tools.add(tool("propose_transaction",
            "Prepare a deposit, withdrawal or transfer for the staff member to confirm. Does not move money by itself.",
            tx, "action", "account_number", "amount"));

        return tools;
    }

    private Map<String, Object> runTool(String name, Map<String, Object> args) {
        try {
            if ("list_accounts".equals(name)) {
                List<Object> list = new ArrayList<>();
                List<Account> sorted = new ArrayList<>(bank.getAllAccounts());
                sorted.sort(Comparator.comparing(Account::getAccountNumber));
                for (Account a : sorted) list.add(describe(a));
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("count", list.size());
                r.put("accounts", list);
                return r;
            }
            if ("get_account".equals(name)) {
                Account a = find(Json.str(args, "account_number"));
                return a == null ? notFound(Json.str(args, "account_number")) : describe(a);
            }
            if ("get_statement".equals(name)) {
                Account a = find(Json.str(args, "account_number"));
                if (a == null) return notFound(Json.str(args, "account_number"));
                Map<String, Object> r = describe(a);
                r.put("history", new ArrayList<Object>(a.getTransactionHistory()));
                r.put("note", "History is kept only while the server runs; the first line is the balance brought forward.");
                return r;
            }
            if ("propose_transaction".equals(name)) {
                return propose(args);
            }
            return error("Unknown tool " + name);
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    private Map<String, Object> propose(Map<String, Object> args) {
        String action = Optional.ofNullable(Json.str(args, "action")).orElse("").toLowerCase();
        if (action.equals("withdrawal")) action = "withdraw";
        if (!Arrays.asList("deposit", "withdraw", "transfer").contains(action)) {
            return error("action must be deposit, withdraw or transfer");
        }
        double amount = Json.num(args, "amount");
        if (Double.isNaN(amount) || amount <= 0) return error("Amount must be a number greater than zero.");
        amount = Math.round(amount * 100) / 100.0;

        Account from = find(Json.str(args, "account_number"));
        if (from == null) return notFound(Json.str(args, "account_number"));

        Account to = null;
        if (action.equals("transfer")) {
            to = find(Json.str(args, "to_account_number"));
            if (to == null) return notFound(Json.str(args, "to_account_number"));
            if (to == from) return error("Source and destination are the same account.");
        }
        if (!action.equals("deposit") && amount > available(from)) {
            return error("Not enough funds in " + from.getAccountNumber() + ". Available: Rs." + available(from));
        }

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", action);
        p.put("accountNumber", from.getAccountNumber());
        p.put("holder", from.getAccountHolder());
        p.put("balance", from.getBalance());
        if (to != null) {
            p.put("toAccount", to.getAccountNumber());
            p.put("toHolder", to.getAccountHolder());
        }
        p.put("amount", amount);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", "shown_for_confirmation");
        r.put("message", "A confirmation card is now shown to the staff member. Nothing has been posted yet.");
        r.put("details", new LinkedHashMap<>(p));
        r.put("proposal", p);
        return r;
    }

    private Account find(String accNo) {
        if (accNo == null) return null;
        return bank.getAccount(accNo.trim().toUpperCase());
    }

    private static double available(Account a) {
        if (a instanceof CurrentAccount) return a.getBalance() + ((CurrentAccount) a).getOverdraftLimit();
        return a.getBalance();
    }

    private static Map<String, Object> describe(Account a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("account_number", a.getAccountNumber());
        m.put("holder", a.getAccountHolder());
        m.put("type", a.getAccountType());
        m.put("balance", a.getBalance());
        if (a instanceof CurrentAccount) m.put("overdraft_limit", ((CurrentAccount) a).getOverdraftLimit());
        m.put("available_to_withdraw", Math.max(0, available(a)));
        return m;
    }

    private static Map<String, Object> notFound(String accNo) {
        return error("No account found with number " + accNo);
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", message);
        return m;
    }

    // ---------- REST call to the model ----------

    private Map<String, Object> callModel(List<Object> messages) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("tools", toolDefinitions());
        body.put("tool_choice", "auto");
        body.put("temperature", 0.2);

        HttpURLConnection con = (HttpURLConnection) new URL(baseUrl + "/chat/completions").openConnection();
        con.setRequestMethod("POST");
        con.setConnectTimeout(10000);
        con.setReadTimeout(90000);
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json");
        if (!apiKey.isEmpty()) con.setRequestProperty("Authorization", "Bearer " + apiKey);
        if (baseUrl.contains("openrouter.ai")) con.setRequestProperty("X-Title", "Kartik Tyagi Small Finance Bank");

        try (OutputStream os = con.getOutputStream()) {
            os.write(Json.write(body).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new Exception("Could not reach " + providerName() + ": " + e.getMessage());
        }

        int code = con.getResponseCode();
        String text = readAll(code >= 400 ? con.getErrorStream() : con.getInputStream());

        if (code >= 400) {
            String detail = text;
            try {
                Map<String, Object> err = Json.obj(Json.obj(Json.parse(text)).get("error"));
                if (err != null && err.get("message") != null) detail = String.valueOf(err.get("message"));
            } catch (Exception ignored) { }
            if (code == 401 || code == 403) throw new Exception("The AI provider rejected the API key. Check AI_API_KEY in ai.properties.");
            if (code == 429) throw new Exception("Free-tier rate limit reached on " + providerName() + ". Wait a minute and try again.");
            throw new Exception(providerName() + " error " + code + ": " + clip(detail, 300));
        }

        Map<String, Object> json = Json.obj(Json.parse(text));
        List<Object> choices = Json.arr(json.get("choices"));
        if (choices == null || choices.isEmpty()) throw new Exception("The AI provider returned no answer.");
        return Json.obj(Json.obj(choices.get(0)).get("message"));
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
        }
        return sb.toString();
    }
}
