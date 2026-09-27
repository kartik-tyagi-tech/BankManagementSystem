import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Thin HTTP layer so the HTML UI can use the existing Bank class.
// Banking rules stay in Bank / Account — this file only routes requests.
public class BankServer {

    private static final int PORT = 8080;
    private static final Bank bank = new Bank();
    private static final AssistantService assistant = new AssistantService(bank);

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/", BankServer::handleApi);
        server.createContext("/", BankServer::handleStatic);
        // A few threads so a slow AI reply doesn't block the rest of the UI.
        // Every Bank access below is synchronized on the bank object.
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("Bank UI running at http://localhost:" + PORT);
        System.out.println("This UI uses your existing Bank class and accounts.txt");
        Map<String, Object> ai = assistant.status();
        System.out.println(Boolean.TRUE.equals(ai.get("configured"))
            ? "Assistant: " + ai.get("provider") + " / " + ai.get("model")
            : "Assistant: not configured (copy ai.properties.example to ai.properties and add a key)");
    }

    private static void handleApi(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            send(exchange, 204, "application/json", "");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        // The assistant route manages its own locking so a slow AI call
        // does not hold the bank lock while waiting on the network.
        if (path.startsWith("/api/assistant")) {
            handleAssistant(exchange, method, path);
            return;
        }

        synchronized (bank) {
            handleBankApi(exchange, method, path);
        }
    }

    private static void handleAssistant(HttpExchange exchange, String method, String path) throws IOException {
        try {
            if ("GET".equals(method) && path.equals("/api/assistant/status")) {
                send(exchange, 200, "application/json", Json.write(assistant.status()));
                return;
            }
            if ("POST".equals(method) && path.equals("/api/assistant")) {
                Map<String, Object> body = Json.obj(Json.parse(readBody(exchange)));
                if (body == null) throw new Exception("Invalid request");
                Map<String, Object> result = assistant.chat(body.get("history"), Json.str(body, "message"));
                send(exchange, 200, "application/json", Json.write(result));
                return;
            }
            send(exchange, 404, "application/json", errorJson("Unknown API route"));
        } catch (Exception e) {
            send(exchange, 400, "application/json", errorJson(e.getMessage()));
        }
    }

    private static void handleBankApi(HttpExchange exchange, String method, String path) throws IOException {
        try {
            if ("GET".equals(method) && path.equals("/api/accounts")) {
                send(exchange, 200, "application/json", accountsJson());
                return;
            }

            if ("GET".equals(method) && path.equals("/api/account")) {
                String accNo = queryParam(exchange, "accNo");
                Account acc = bank.getAccount(accNo);
                if (acc == null) {
                    send(exchange, 404, "application/json", errorJson("Account not found!"));
                    return;
                }
                send(exchange, 200, "application/json", accountJson(acc));
                return;
            }

            if ("POST".equals(method) && path.equals("/api/accounts/savings")) {
                String body = readBody(exchange);
                Account acc = bank.createSavingsAccount(
                    jsonString(body, "name"),
                    jsonNumber(body, "balance")
                );
                send(exchange, 200, "application/json", accountJson(acc));
                return;
            }

            if ("POST".equals(method) && path.equals("/api/accounts/current")) {
                String body = readBody(exchange);
                Account acc = bank.createCurrentAccount(
                    jsonString(body, "name"),
                    jsonNumber(body, "balance"),
                    jsonNumber(body, "overdraft")
                );
                send(exchange, 200, "application/json", accountJson(acc));
                return;
            }

            if ("POST".equals(method) && path.equals("/api/deposit")) {
                String body = readBody(exchange);
                bank.deposit(jsonString(body, "accountNumber"), jsonNumber(body, "amount"));
                send(exchange, 200, "application/json", "{\"ok\":true}");
                return;
            }

            if ("POST".equals(method) && path.equals("/api/withdraw")) {
                String body = readBody(exchange);
                bank.withdraw(jsonString(body, "accountNumber"), jsonNumber(body, "amount"));
                send(exchange, 200, "application/json", "{\"ok\":true}");
                return;
            }

            if ("POST".equals(method) && path.equals("/api/transfer")) {
                String body = readBody(exchange);
                bank.transfer(
                    jsonString(body, "fromAcc"),
                    jsonString(body, "toAcc"),
                    jsonNumber(body, "amount")
                );
                send(exchange, 200, "application/json", "{\"ok\":true}");
                return;
            }

            send(exchange, 404, "application/json", errorJson("Unknown API route"));
        } catch (Exception e) {
            send(exchange, 400, "application/json", errorJson(e.getMessage()));
        }
    }

    private static void handleStatic(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            send(exchange, 204, "text/plain", "");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";

        Path file = Paths.get(".").toAbsolutePath().normalize().resolve(path.substring(1)).normalize();
        Path root = Paths.get(".").toAbsolutePath().normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            send(exchange, 404, "text/plain", "Not found");
            return;
        }

        byte[] bytes = Files.readAllBytes(file);
        String name = file.getFileName().toString();
        String type = "text/plain";
        if (name.endsWith(".html")) type = "text/html; charset=utf-8";
        else if (name.endsWith(".css")) type = "text/css; charset=utf-8";
        else if (name.endsWith(".js")) type = "application/javascript; charset=utf-8";
        else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) type = "image/jpeg";
        else if (name.endsWith(".png")) type = "image/png";
        else if (name.endsWith(".svg")) type = "image/svg+xml";

        exchange.getResponseHeaders().set("Content-Type", type);
        addCors(exchange);
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private static String accountsJson() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Account acc : bank.getAllAccounts()) {
            if (!first) sb.append(",");
            sb.append(accountJson(acc));
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }

    private static String accountJson(Account acc) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"type\":\"").append(escape(acc.getAccountType())).append("\",");
        sb.append("\"accountNumber\":\"").append(escape(acc.getAccountNumber())).append("\",");
        sb.append("\"accountHolder\":\"").append(escape(acc.getAccountHolder())).append("\",");
        sb.append("\"balance\":").append(acc.getBalance());
        if (acc instanceof CurrentAccount) {
            sb.append(",\"overdraftLimit\":").append(((CurrentAccount) acc).getOverdraftLimit());
        }
        sb.append(",\"transactionHistory\":[");
        boolean first = true;
        for (String txn : acc.getTransactionHistory()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escape(txn)).append("\"");
            first = false;
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String errorJson(String message) {
        return "{\"error\":\"" + escape(message == null ? "Error" : message) + "\"}";
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)
        );
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private static String jsonString(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return "";
    }

    private static double jsonNumber(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) return Double.parseDouble(matcher.group(1));
        return 0;
    }

    private static String queryParam(HttpExchange exchange, String key) throws UnsupportedEncodingException {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) return "";
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return URLDecoder.decode(kv[1], "UTF-8");
            }
        }
        return "";
    }

    private static void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        addCors(exchange);
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
}
