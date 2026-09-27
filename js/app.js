// Front end for BankServer.java — talks to the same /api routes as before.

const VIEWS = {
  dashboard: ["Overview", "Book position"],
  accounts:  ["Accounts", "All accounts"],
  create:    ["Accounts", "Open an account"],
  deposit:   ["Counter", "Cash deposit"],
  withdraw:  ["Counter", "Cash withdrawal"],
  transfer:  ["Counter", "Transfer between accounts"],
  balance:   ["Enquiry", "Balance enquiry"],
  history:   ["Enquiry", "Account statement"],
  assistant: ["Help desk", "Banking assistant"]
};

let accounts = [];          // last list fetched from the server
let accountFilter = "ALL";

/* ---------- helpers ---------- */

const $ = (id) => document.getElementById(id);

const inr = new Intl.NumberFormat("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });

function money(value) {
  const n = Number(value) || 0;
  return (n < 0 ? "−₹" : "₹") + inr.format(Math.abs(n));
}

// Big serif figure: rupees large, paise smaller.
function moneyFigure(value) {
  const [whole, paise] = money(value).split(".");
  return escapeHtml(whole) + '<span class="paise">.' + paise + "</span>";
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>'"]/g, (c) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;"
  })[c]);
}

function typeLabel(type) {
  return type === "CURRENT" ? "Current" : "Savings";
}

function typeTag(type) {
  return '<span class="tag' + (type === "CURRENT" ? " cur" : "") + '">' + typeLabel(type) + "</span>";
}

function holder(acc) {
  return acc.accountHolder ? escapeHtml(acc.accountHolder) : '<span class="noname">No name on record</span>';
}

function nowStamp() {
  return new Date().toLocaleString("en-IN", {
    day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit"
  });
}

let toastTimer;
function toast(message, isError) {
  const el = $("toast");
  el.textContent = message;
  el.classList.toggle("error", Boolean(isError));
  el.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.hidden = true; }, isError ? 6000 : 3500);
}

function setServer(ok) {
  $("server-dot").className = "dot " + (ok ? "ok" : "down");
  $("server-status").textContent = ok ? "Server online" : "Server not reachable";
}

async function api(path, options) {
  let response;
  try {
    response = await fetch(path, options);
  } catch (e) {
    setServer(false);
    throw new Error("Cannot reach the server. Start it with: java BankServer");
  }
  setServer(true);
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.error || "Request failed");
  return data;
}

function post(path, body) {
  return api(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });
}

async function refreshAccounts() {
  accounts = await api("/api/accounts");
  accounts.sort((a, b) => a.accountNumber.localeCompare(b.accountNumber));
  $("acc-list").innerHTML = accounts
    .map((a) => '<option value="' + escapeHtml(a.accountNumber) + '">' + escapeHtml(a.accountHolder || "") + "</option>")
    .join("");
  return accounts;
}

function findAccount(accNo) {
  return accounts.find((a) => a.accountNumber.toUpperCase() === accNo.trim().toUpperCase());
}

/* ---------- account register ---------- */

function registerTable(list, withActions) {
  if (!list.length) return "";
  const total = list.reduce((s, a) => s + a.balance, 0);
  return (
    '<div class="table-wrap"><table class="ledger"><thead><tr>' +
    "<th>Account no.</th><th>Holder</th><th>Type</th>" +
    '<th class="num">Overdraft limit</th><th class="num">Balance</th>' +
    (withActions ? "<th></th>" : "") +
    "</tr></thead><tbody>" +
    list.map((a) =>
      '<tr class="click" data-acc="' + escapeHtml(a.accountNumber) + '">' +
      '<td class="acc">' + escapeHtml(a.accountNumber) + "</td>" +
      "<td>" + holder(a) + "</td>" +
      "<td>" + typeTag(a.type) + "</td>" +
      '<td class="num dim">' + (a.type === "CURRENT" ? money(a.overdraftLimit) : "—") + "</td>" +
      '<td class="num' + (a.balance < 0 ? " neg" : "") + '">' + money(a.balance) + "</td>" +
      (withActions
        ? '<td class="row-actions"><button type="button" data-go="deposit">Deposit</button><button type="button" data-go="withdraw">Withdraw</button><button type="button" data-go="history">Statement</button></td>'
        : "") +
      "</tr>"
    ).join("") +
    "</tbody><tfoot><tr><td colspan=\"4\">" + list.length + (list.length === 1 ? " account" : " accounts") +
    '</td><td class="num">' + money(total) + "</td>" + (withActions ? "<td></td>" : "") + "</tr></tfoot></table></div>"
  );
}

async function renderDashboard() {
  try {
    await refreshAccounts();
  } catch (err) {
    $("dashboard-list").innerHTML = '<div class="empty">' + escapeHtml(err.message) + "</div>";
    return;
  }
  const total = accounts.reduce((s, a) => s + a.balance, 0);
  const sav = accounts.filter((a) => a.type === "SAVINGS");
  const cur = accounts.filter((a) => a.type === "CURRENT");

  $("stat-balance").innerHTML = moneyFigure(total);
  $("stat-accounts").textContent = accounts.length;
  $("stat-savings").textContent = sav.length;
  $("stat-current").textContent = cur.length;

  // bar shows how deposits split between savings and current
  const savSum = Math.max(0, sav.reduce((s, a) => s + a.balance, 0));
  const curSum = Math.max(0, cur.reduce((s, a) => s + a.balance, 0));
  const base = savSum + curSum || 1;
  $("split-sav").style.width = (savSum / base * 100) + "%";
  $("split-cur").style.width = (curSum / base * 100) + "%";

  $("dashboard-list").innerHTML = accounts.length
    ? registerTable(accounts, false)
    : '<div class="empty">No accounts on the register yet. <a href="#create">Open the first one</a>.</div>';
}

async function renderAllAccounts() {
  try {
    await refreshAccounts();
  } catch (err) {
    $("all-accounts").innerHTML = '<div class="empty">' + escapeHtml(err.message) + "</div>";
    return;
  }
  drawAccountList();
}

function drawAccountList() {
  const q = $("account-filter").value.trim().toLowerCase();
  const list = accounts.filter((a) =>
    (accountFilter === "ALL" || a.type === accountFilter) &&
    (!q || a.accountNumber.toLowerCase().includes(q) || (a.accountHolder || "").toLowerCase().includes(q))
  );
  $("all-accounts").innerHTML = list.length
    ? registerTable(list, true)
    : '<div class="empty">' + (accounts.length ? "No accounts match this filter." : 'No accounts yet. <a href="#create">Open one</a>.') + "</div>";
}

/* ---------- receipts ---------- */

function receipt(target, title, rows, totalLabel, totalValue) {
  $(target).innerHTML =
    '<div class="slip-title"><strong>' + escapeHtml(title) + "</strong><span>" + escapeHtml(nowStamp()) + "</span></div>" +
    "<dl>" + rows.map(([k, v]) => "<div><dt>" + escapeHtml(k) + "</dt><dd>" + v + "</dd></div>").join("") + "</dl>" +
    '<dl class="total"><div><dt>' + escapeHtml(totalLabel) + "</dt><dd>" + totalValue + "</dd></div></dl>" +
    '<div class="stamp">Posted</div>';
}

/* ---------- statement ---------- */

function parseHistory(acc) {
  const num = (s) => Number(s.replace(/,/g, ""));
  return (acc.transactionHistory || []).map((line) => {
    let m;
    if ((m = line.match(/^Account created with balance: Rs\.([\d.E+-]+)/i))) {
      return { text: "Opening balance / brought forward", credit: null, debit: null, balance: num(m[1]) };
    }
    if ((m = line.match(/^Deposited: Rs\.([\d.E+-]+) \| Balance: Rs\.([\d.E+-]+)/i))) {
      return { text: "Deposit", credit: num(m[1]), debit: null, balance: num(m[2]) };
    }
    if ((m = line.match(/^Withdrawn: Rs\.([\d.E+-]+) \| Balance: Rs\.([\d.E+-]+)/i))) {
      return { text: "Withdrawal", credit: null, debit: num(m[1]), balance: num(m[2]) };
    }
    return { text: line, credit: null, debit: null, balance: null };
  });
}

function renderStatement(acc) {
  const rows = parseHistory(acc);
  const cr = rows.reduce((s, r) => s + (r.credit || 0), 0);
  const dr = rows.reduce((s, r) => s + (r.debit || 0), 0);
  $("history-result").innerHTML =
    '<div class="statement-head"><h3>' + holder(acc) + ' <span class="mono">· ' + escapeHtml(acc.accountNumber) + "</span></h3>" +
    "<span>" + typeLabel(acc.type) + " account</span></div>" +
    '<div class="table-wrap"><table class="ledger"><thead><tr><th style="width:48px">#</th><th>Particulars</th>' +
    '<th class="num">Debit</th><th class="num">Credit</th><th class="num">Balance</th></tr></thead><tbody>' +
    rows.map((r, i) =>
      '<tr><td class="dim mono">' + String(i + 1).padStart(2, "0") + "</td>" +
      "<td>" + escapeHtml(r.text) + "</td>" +
      '<td class="num dr">' + (r.debit != null ? money(r.debit) : "") + "</td>" +
      '<td class="num cr">' + (r.credit != null ? money(r.credit) : "") + "</td>" +
      '<td class="num' + (r.balance < 0 ? " neg" : "") + '">' + (r.balance != null ? money(r.balance) : "") + "</td></tr>"
    ).join("") +
    '</tbody><tfoot><tr><td></td><td>Totals · closing balance</td><td class="num dr">' + money(dr) +
    '</td><td class="num cr">' + money(cr) + '</td><td class="num">' + money(acc.balance) + "</td></tr></tfoot></table></div>" +
    '<p class="note">Entries are held by the server for the current session only. After the server restarts, the statement begins from the balance brought forward in accounts.txt.</p>';
}

function renderBalance(acc) {
  const avail = acc.type === "CURRENT" ? acc.balance + (acc.overdraftLimit || 0) : acc.balance;
  $("balance-result").innerHTML =
    '<div class="account-sheet">' +
    '<div class="sheet-top"><div><h3>' + holder(acc) + '</h3><div class="acc">' + escapeHtml(acc.accountNumber) + "</div></div>" + typeTag(acc.type) + "</div>" +
    '<dl class="sheet-grid">' +
    "<div><dt>Ledger balance</dt><dd" + (acc.balance < 0 ? ' class="neg"' : "") + ">" + moneyFigure(acc.balance) + "</dd></div>" +
    "<div><dt>Overdraft limit</dt><dd>" + (acc.type === "CURRENT" ? moneyFigure(acc.overdraftLimit) : "—") + "</dd></div>" +
    "<div><dt>Available to withdraw</dt><dd>" + moneyFigure(Math.max(0, avail)) + "</dd></div>" +
    "</dl>" +
    '<div class="sheet-links"><button type="button" data-go="deposit">Deposit</button><button type="button" data-go="withdraw">Withdraw</button><button type="button" data-go="history">View statement</button></div>' +
    "</div>";
  $("balance-result").dataset.acc = acc.accountNumber;
}

/* ---------- navigation ---------- */

function showView(name, prefillAcc) {
  if (!VIEWS[name]) name = "dashboard";
  document.querySelectorAll(".view").forEach((v) => { v.hidden = v.id !== "view-" + name; });
  document.querySelectorAll(".menu a").forEach((a) => a.classList.toggle("on", a.dataset.view === name));
  $("page-eyebrow").textContent = VIEWS[name][0];
  $("page-title").textContent = VIEWS[name][1];
  document.title = VIEWS[name][1] + " · Kartik Tyagi Small Finance Bank";

  if (name === "dashboard") renderDashboard();
  if (name === "accounts") renderAllAccounts();
  if (name === "assistant") openAssistant();

  if (prefillAcc) {
    const field = { deposit: "deposit-acc", withdraw: "withdraw-acc", transfer: "transfer-from", balance: "balance-acc", history: "history-acc" }[name];
    if (field) {
      $(field).value = prefillAcc;
      $(field).dispatchEvent(new Event("input"));
      if (name === "history") $("form-history").requestSubmit();
      if (name === "balance") $("form-balance").requestSubmit();
      const next = { deposit: "deposit-amount", withdraw: "withdraw-amount", transfer: "transfer-to" }[name];
      if (next) $(next).focus();
    }
  }
}

function go(name, acc) {
  if (location.hash !== "#" + name) {
    history.pushState(null, "", "#" + name);
  }
  showView(name, acc);
}

window.addEventListener("popstate", () => showView(location.hash.slice(1)));

document.querySelector(".menu").addEventListener("click", (e) => {
  const a = e.target.closest("a[data-view]");
  if (!a) return;
  e.preventDefault();
  go(a.dataset.view);
});

document.addEventListener("click", (e) => {
  const link = e.target.closest('a[href^="#"]');
  if (link && !link.closest(".menu")) {
    e.preventDefault();
    go(link.getAttribute("href").slice(1));
    return;
  }
  const action = e.target.closest("[data-go]");
  if (action) {
    const acc = action.closest("[data-acc]")?.dataset.acc;
    go(action.dataset.go, acc);
    return;
  }
  const row = e.target.closest("tr.click[data-acc]");
  if (row) go("balance", row.dataset.acc);
});

/* ---------- live holder-name lookup under account fields ---------- */

document.querySelectorAll(".acc-input").forEach((input) => {
  input.addEventListener("input", () => {
    const hint = document.querySelector('.who[data-for="' + input.id + '"]');
    if (!hint) return;
    const v = input.value.trim();
    const acc = v && findAccount(v);
    if (!v) { hint.textContent = ""; hint.className = "who"; return; }
    if (acc) {
      hint.textContent = (acc.accountHolder || "No name on record") + " · " + typeLabel(acc.type) + " · " + money(acc.balance);
      hint.className = "who found";
    } else {
      hint.textContent = v.length >= 6 ? "No account with this number" : "";
      hint.className = "who missing";
    }
  });
  input.addEventListener("blur", () => { input.value = input.value.trim().toUpperCase(); });
});

/* ---------- account type toggle ---------- */

document.querySelectorAll('input[name="create-type"]').forEach((r) => {
  r.addEventListener("change", () => {
    $("overdraft-field").hidden = document.querySelector('input[name="create-type"]:checked').value !== "2";
  });
});

$("account-filter").addEventListener("input", drawAccountList);
document.querySelectorAll(".seg button").forEach((b) => {
  b.addEventListener("click", () => {
    accountFilter = b.dataset.filter;
    document.querySelectorAll(".seg button").forEach((x) => x.classList.toggle("on", x === b));
    drawAccountList();
  });
});

/* ---------- forms ---------- */

async function withBusy(form, fn) {
  const btn = form.querySelector('button[type="submit"]');
  btn.disabled = true;
  try { await fn(); }
  catch (err) { toast(err.message, true); }
  finally { btn.disabled = false; }
}

function clearHints(form) {
  form.querySelectorAll(".who").forEach((h) => { h.textContent = ""; h.className = "who"; });
}

$("form-create").addEventListener("submit", (e) => {
  e.preventDefault();
  const form = e.target;
  withBusy(form, async () => {
    const isCurrent = document.querySelector('input[name="create-type"]:checked').value === "2";
    const name = $("create-name").value.trim();
    const balance = Number($("create-balance").value);
    const acc = isCurrent
      ? await post("/api/accounts/current", { name, balance, overdraft: Number($("create-overdraft").value) || 0 })
      : await post("/api/accounts/savings", { name, balance });

    const rows = [
      ["Account no.", '<b>' + escapeHtml(acc.accountNumber) + "</b>"],
      ["Holder", escapeHtml(acc.accountHolder)],
      ["Type", typeLabel(acc.type)]
    ];
    if (acc.type === "CURRENT") rows.push(["Overdraft limit", money(acc.overdraftLimit)]);
    receipt("create-result", "Account opened", rows, "Opening balance", money(acc.balance));

    toast("Account " + acc.accountNumber + " opened for " + acc.accountHolder);
    form.reset();
    $("overdraft-field").hidden = true;
    refreshAccounts().catch(() => {});
  });
});

$("form-deposit").addEventListener("submit", (e) => {
  e.preventDefault();
  const form = e.target;
  withBusy(form, async () => {
    const accNo = $("deposit-acc").value.trim().toUpperCase();
    const amount = Number($("deposit-amount").value);
    await post("/api/deposit", { accountNumber: accNo, amount });
    const acc = await api("/api/account?accNo=" + encodeURIComponent(accNo));
    receipt("deposit-result", "Deposit", [
      ["Account no.", escapeHtml(acc.accountNumber)],
      ["Holder", holder(acc)],
      ["Amount credited", money(amount)]
    ], "Balance after", money(acc.balance));
    form.reset(); clearHints(form);
    refreshAccounts().catch(() => {});
  });
});

$("form-withdraw").addEventListener("submit", (e) => {
  e.preventDefault();
  const form = e.target;
  withBusy(form, async () => {
    const accNo = $("withdraw-acc").value.trim().toUpperCase();
    const amount = Number($("withdraw-amount").value);
    await post("/api/withdraw", { accountNumber: accNo, amount });
    const acc = await api("/api/account?accNo=" + encodeURIComponent(accNo));
    receipt("withdraw-result", "Withdrawal", [
      ["Account no.", escapeHtml(acc.accountNumber)],
      ["Holder", holder(acc)],
      ["Amount debited", money(amount)]
    ], "Balance after", money(acc.balance));
    form.reset(); clearHints(form);
    refreshAccounts().catch(() => {});
  });
});

$("form-transfer").addEventListener("submit", (e) => {
  e.preventDefault();
  const form = e.target;
  withBusy(form, async () => {
    const fromAcc = $("transfer-from").value.trim().toUpperCase();
    const toAcc = $("transfer-to").value.trim().toUpperCase();
    const amount = Number($("transfer-amount").value);
    if (fromAcc === toAcc) throw new Error("Source and destination must be different accounts.");
    await post("/api/transfer", { fromAcc, toAcc, amount });
    const [src, dst] = await Promise.all([
      api("/api/account?accNo=" + encodeURIComponent(fromAcc)),
      api("/api/account?accNo=" + encodeURIComponent(toAcc))
    ]);
    receipt("transfer-result", "Transfer", [
      ["From", escapeHtml(src.accountNumber) + " · " + holder(src)],
      ["To", escapeHtml(dst.accountNumber) + " · " + holder(dst)],
      ["Amount", money(amount)],
      ["To-account balance", money(dst.balance)]
    ], "From-account balance", money(src.balance));
    form.reset(); clearHints(form);
    refreshAccounts().catch(() => {});
  });
});

$("form-balance").addEventListener("submit", (e) => {
  e.preventDefault();
  const form = e.target;
  withBusy(form, async () => {
    const accNo = $("balance-acc").value.trim().toUpperCase();
    try {
      renderBalance(await api("/api/account?accNo=" + encodeURIComponent(accNo)));
    } catch (err) {
      $("balance-result").innerHTML = '<div class="empty">No account found with number <span class="mono">' + escapeHtml(accNo) + "</span>.</div>";
      throw err;
    }
  });
});

$("form-history").addEventListener("submit", (e) => {
  e.preventDefault();
  const form = e.target;
  withBusy(form, async () => {
    const accNo = $("history-acc").value.trim().toUpperCase();
    try {
      renderStatement(await api("/api/account?accNo=" + encodeURIComponent(accNo)));
    } catch (err) {
      $("history-result").innerHTML = '<div class="empty">No account found with number <span class="mono">' + escapeHtml(accNo) + "</span>.</div>";
      throw err;
    }
  });
});

// Buttons inside the balance sheet use the looked-up account
$("balance-result").addEventListener("click", (e) => {
  const b = e.target.closest("[data-go]");
  if (!b) return;
  e.stopPropagation();
  go(b.dataset.go, $("balance-result").dataset.acc);
}, true);


/* ---------- AI assistant ---------- */

const chat = [];            // {role: "user"|"assistant", content} sent back to the server
let chatBusy = false;
let aiStatusLoaded = false;

async function openAssistant() {
  if (!aiStatusLoaded) {
    try {
      const st = await api("/api/assistant/status");
      $("ai-model").textContent = st.model;
      $("ai-provider").textContent = st.provider;
      $("ai-setup").hidden = st.configured;
      aiStatusLoaded = true;
    } catch (e) { /* server down: shown in sidebar */ }
  }
  refreshAccounts().catch(() => {});
  setTimeout(() => $("chat-input").focus(), 0);
}

// Small, safe formatter: escapes everything, then allows **bold**, `code` and "- " lists.
function formatReply(text) {
  const inline = (t) => escapeHtml(t)
    .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
    .replace(/`([^`]+)`/g, "<code>$1</code>");
  const blocks = [];
  let list = null;
  String(text || "").split(/\n/).forEach((raw) => {
    const line = raw.trim();
    const item = line.match(/^(?:[-*•]|\d+[.)])\s+(.*)/);
    if (item) {
      if (!list) { list = []; blocks.push({ list }); }
      list.push(inline(item[1]));
    } else if (line) {
      list = null;
      blocks.push({ p: inline(line) });
    } else {
      list = null;
    }
  });
  return blocks.map((b) => b.list ? "<ul>" + b.list.map((i) => "<li>" + i + "</li>").join("") + "</ul>" : "<p>" + b.p + "</p>").join("")
    || "<p class=\"thinking\">(no reply)</p>";
}

function addTurn(kind, html) {
  $("chat-intro").hidden = true;
  const el = document.createElement("div");
  el.className = "turn " + kind;
  el.innerHTML = '<div class="who-label">' + (kind === "user" ? "You" : "Assistant") + '</div><div class="turn-body">' + html + "</div>";
  $("chat-log").appendChild(el);
  $("chat-log").scrollTop = $("chat-log").scrollHeight;
  return el;
}

const ACTION_TITLES = { deposit: "Confirm deposit", withdraw: "Confirm withdrawal", transfer: "Confirm transfer" };

function confirmCard(action) {
  const rows = [];
  if (action.type === "transfer") {
    rows.push(["From", escapeHtml(action.accountNumber) + " · " + escapeHtml(action.holder || "—")]);
    rows.push(["To", escapeHtml(action.toAccount) + " · " + escapeHtml(action.toHolder || "—")]);
  } else {
    rows.push(["Account", escapeHtml(action.accountNumber)]);
    rows.push(["Holder", escapeHtml(action.holder || "—")]);
  }
  rows.push(["Current balance", money(action.balance)]);
  const card = document.createElement("div");
  card.className = "confirm";
  card.innerHTML =
    "<h4>" + ACTION_TITLES[action.type] + "</h4><dl>" +
    rows.map(([k, v]) => "<div><dt>" + k + "</dt><dd>" + v + "</dd></div>").join("") +
    '<div class="amt"><dt>Amount</dt><dd>' + money(action.amount) + "</dd></div></dl>" +
    '<div class="confirm-actions"><button type="button" class="btn">Confirm and post</button><button type="button" class="btn-plain">Cancel</button></div>';

  const [ok, cancel] = card.querySelectorAll("button");
  const finish = (cls, text, note) => {
    card.querySelector(".confirm-actions").remove();
    card.classList.add(cls === "ok" ? "done" : "cancelled");
    const st = document.createElement("div");
    st.className = "confirm-state " + cls;
    st.textContent = text;
    card.appendChild(st);
    if (note) chat.push({ role: "assistant", content: note });
  };

  ok.addEventListener("click", async () => {
    ok.disabled = true; cancel.disabled = true;
    try {
      if (action.type === "deposit") await post("/api/deposit", { accountNumber: action.accountNumber, amount: action.amount });
      if (action.type === "withdraw") await post("/api/withdraw", { accountNumber: action.accountNumber, amount: action.amount });
      if (action.type === "transfer") await post("/api/transfer", { fromAcc: action.accountNumber, toAcc: action.toAccount, amount: action.amount });
      const acc = await api("/api/account?accNo=" + encodeURIComponent(action.accountNumber));
      finish("ok", "Posted · " + acc.accountNumber + " balance now " + money(acc.balance),
        "[Staff confirmed and posted the " + action.type + " of " + money(action.amount) + " on " + action.accountNumber +
        (action.toAccount ? " to " + action.toAccount : "") + ". New balance of " + acc.accountNumber + ": " + money(acc.balance) + ".]");
      refreshAccounts().catch(() => {});
    } catch (err) {
      ok.disabled = false; cancel.disabled = false;
      toast(err.message, true);
    }
  });
  cancel.addEventListener("click", () => {
    finish("no", "Cancelled · nothing was posted", "[Staff cancelled the proposed " + action.type + ". Nothing was posted.]");
  });
  return card;
}

async function sendChat(text) {
  text = text.trim();
  if (!text || chatBusy) return;
  chatBusy = true;
  $("chat-send").disabled = true;
  $("chat-input").value = "";

  addTurn("user", escapeHtml(text));
  const pending = addTurn("bot", '<span class="thinking">Checking</span>');
  const history = chat.slice(-20);

  try {
    const res = await post("/api/assistant", { message: text, history });
    chat.push({ role: "user", content: text });
    chat.push({ role: "assistant", content: res.reply || "" });
    const body = pending.querySelector(".turn-body");
    body.innerHTML = formatReply(res.reply);
    if (res.action) body.appendChild(confirmCard(res.action));
  } catch (err) {
    pending.classList.add("err");
    pending.querySelector(".turn-body").textContent = err.message;
  } finally {
    chatBusy = false;
    $("chat-send").disabled = false;
    $("chat-log").scrollTop = $("chat-log").scrollHeight;
    $("chat-input").focus();
  }
}

$("chat-form").addEventListener("submit", (e) => {
  e.preventDefault();
  sendChat($("chat-input").value);
});

$("chat-input").addEventListener("keydown", (e) => {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    sendChat($("chat-input").value);
  }
});

document.querySelectorAll(".try").forEach((b) => b.addEventListener("click", () => sendChat(b.textContent)));

$("chat-clear").addEventListener("click", () => {
  chat.length = 0;
  $("chat-log").querySelectorAll(".turn").forEach((t) => t.remove());
  $("chat-intro").hidden = false;
});

/* ---------- start ---------- */

$("today").textContent = new Date().toLocaleDateString("en-IN", {
  weekday: "long", day: "numeric", month: "long", year: "numeric"
});

refreshAccounts().catch(() => setServer(false));
showView(location.hash.slice(1) || "dashboard");
