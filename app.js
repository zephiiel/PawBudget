"use strict";

const STORAGE_KEY = "ledger.monthly-tracker.v1";
const CHART_COLORS = ["#171717", "#555552", "#888883", "#b7b7b1", "#d8d8d2"];
const formatter = new Intl.NumberFormat("en-PH", {
  style: "currency",
  currency: "PHP",
  minimumFractionDigits: 2,
});
const compactFormatter = new Intl.NumberFormat("en-PH", {
  style: "currency",
  currency: "PHP",
  maximumFractionDigits: 0,
});
const dateFormatter = new Intl.DateTimeFormat("en-PH", {
  day: "numeric",
  month: "short",
  year: "numeric",
});
const state = {
  data: null,
  month: null,
  page: "overview",
};

const elements = {
  alert: document.getElementById("app-alert"),
  saveStatus: document.getElementById("save-status"),
  mobileSaveStatus: document.getElementById("mobile-save-status"),
  overviewPage: document.getElementById("overview-page"),
  pendingPage: document.getElementById("pending-page"),
  transactionsPage: document.getElementById("transactions-page"),
  budgetsPage: document.getElementById("budgets-page"),
  currentMonthLabel: document.getElementById("current-month-label"),
  totalUsed: document.getElementById("total-used"),
  creditUsed: document.getElementById("credit-used"),
  paidUsed: document.getElementById("paid-used"),
  totalBudget: document.getElementById("total-budget"),
  creditUsedCaption: document.getElementById("credit-used-caption"),
  categoryDonut: document.getElementById("category-donut"),
  categoryLegend: document.getElementById("category-legend"),
  donutTotal: document.getElementById("donut-total"),
  categoryEmpty: document.getElementById("category-empty"),
  categoryBars: document.getElementById("category-bars"),
  barsEmpty: document.getElementById("bars-empty"),
  accountBars: document.getElementById("account-bars"),
  accountsEmpty: document.getElementById("accounts-empty"),
  categoryTransactionsDialog: document.getElementById("category-transactions-dialog"),
  categoryTransactionsTitle: document.getElementById("category-transactions-title"),
  categoryTransactionsList: document.getElementById("category-transactions-list"),
  pendingList: document.getElementById("pending-list"),
  paidCreditList: document.getElementById("paid-credit-list"),
  pendingTotal: document.getElementById("pending-total"),
  pendingCount: document.getElementById("pending-count"),
  pendingNavCount: document.getElementById("pending-nav-count"),
  transactionDialog: document.getElementById("transaction-dialog"),
  transactionForm: document.getElementById("transaction-form"),
  transactionDialogTitle: document.getElementById("transaction-dialog-title"),
  transactionId: document.getElementById("transaction-id"),
  transactionDescription: document.getElementById("transaction-description"),
  transactionDate: document.getElementById("transaction-date"),
  transactionAmount: document.getElementById("transaction-amount"),
  transactionCategory: document.getElementById("transaction-category"),
  transactionAccount: document.getElementById("transaction-account"),
  saveTransactionButton: document.getElementById("save-transaction-button"),
  transactionList: document.getElementById("transaction-list"),
  budgetPeriodLabel: document.getElementById("budget-period-label"),
  budgetsPageTotal: document.getElementById("budgets-page-total"),
  budgetEntryCount: document.getElementById("budget-entry-count"),
  budgetList: document.getElementById("budget-list"),
  budgetDialog: document.getElementById("budget-dialog"),
  budgetForm: document.getElementById("budget-form"),
  budgetDialogTitle: document.getElementById("budget-dialog-title"),
  budgetId: document.getElementById("budget-id"),
  budgetPerson: document.getElementById("budget-person"),
  budgetAmount: document.getElementById("budget-amount"),
  saveBudgetButton: document.getElementById("save-budget-button"),
  manageDialog: document.getElementById("manage-dialog"),
  cycleStartDay: document.getElementById("cycle-start-day"),
  categoryList: document.getElementById("category-list"),
  accountList: document.getElementById("account-list"),
};

function localDateString(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function getSelectedPeriodStart() {
  return localDateString(getCycleStartDate(state.month.getFullYear(), state.month.getMonth()));
}

function getPeriodBudgets() {
  const periodStart = getSelectedPeriodStart();
  return state.data.budgets.filter((budget) => budget.periodStart === periodStart);
}

function exampleDate(daysAgo) {
  const today = new Date();
  const day = Math.max(1, today.getDate() - daysAgo);
  return localDateString(new Date(today.getFullYear(), today.getMonth(), day));
}

function renderBudgets() {
  const budgets = [...getPeriodBudgets()].sort((a, b) => a.person.localeCompare(b.person));
  const total = budgets.reduce((sum, budget) => sum + budget.amount, 0);
  const { start, end } = getSelectedCycle();
  elements.budgetPeriodLabel.textContent = `${dateFormatter.format(start)} – ${dateFormatter.format(end)}`;
  elements.budgetsPageTotal.textContent = formatAmount(total);
  elements.budgetEntryCount.textContent = String(budgets.length);
  elements.budgetList.replaceChildren();
  if (!budgets.length) {
    elements.budgetList.append(createEmptyState("No budgets for this period", "Add a budget to record the amount contributed by a person.", "Add budget", openNewBudget));
    return;
  }
  for (const budget of budgets) {
    const item = document.createElement("article");
    item.className = "budget-item";
    const primary = document.createElement("div");
    primary.className = "item-primary";
    const mark = document.createElement("span");
    mark.className = "item-mark";
    mark.textContent = "₱";
    const copy = document.createElement("div");
    copy.className = "item-copy";
    const title = document.createElement("strong");
    title.textContent = budget.person;
    const metadata = document.createElement("span");
    metadata.textContent = `Budget for ${dateFormatter.format(start)} – ${dateFormatter.format(end)}`;
    copy.append(title, metadata);
    primary.append(mark, copy);
    const actions = document.createElement("div");
    actions.className = "transaction-actions";
    const amount = document.createElement("span");
    amount.className = "item-amount";
    amount.textContent = formatAmount(budget.amount);
    const editButton = document.createElement("button");
    editButton.className = "small-action";
    editButton.type = "button";
    editButton.textContent = "Edit";
    editButton.addEventListener("click", () => openEditBudget(budget.id));
    const deleteButton = document.createElement("button");
    deleteButton.className = "small-action is-danger";
    deleteButton.type = "button";
    deleteButton.textContent = "Delete";
    deleteButton.addEventListener("click", () => deleteBudget(budget.id));
    actions.append(amount, editButton, deleteButton);
    item.append(primary, actions);
    elements.budgetList.append(item);
  }
}

function openNewBudget() {
  elements.budgetForm.reset();
  elements.budgetId.value = "";
  elements.budgetDialogTitle.textContent = "Add budget";
  elements.saveBudgetButton.textContent = "Save budget";
  elements.budgetDialog.showModal();
}

function openEditBudget(id) {
  const budget = state.data.budgets.find((item) => item.id === id);
  if (!budget) return;
  elements.budgetId.value = budget.id;
  elements.budgetPerson.value = budget.person;
  elements.budgetAmount.value = String(budget.amount);
  elements.budgetDialogTitle.textContent = "Edit budget";
  elements.saveBudgetButton.textContent = "Save changes";
  elements.budgetDialog.showModal();
}

function submitBudget(event) {
  event.preventDefault();
  const person = elements.budgetPerson.value.trim();
  const amount = Number(elements.budgetAmount.value);
  if (!person || !Number.isFinite(amount) || amount <= 0) {
    showAlert("Enter a person’s name and a valid budget amount greater than zero.");
    return;
  }
  const id = elements.budgetId.value;
  const existing = state.data.budgets.find((budget) => budget.id === id);
  const budget = {
    id: id || `budget-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    periodStart: existing ? existing.periodStart : getSelectedPeriodStart(),
    person,
    amount,
  };
  const budgets = existing
    ? state.data.budgets.map((item) => item.id === id ? budget : item)
    : [...state.data.budgets, budget];
  if (!saveData({ ...state.data, budgets })) return;
  elements.budgetDialog.close();
  updateAll();
}

function deleteBudget(id) {
  const budget = state.data.budgets.find((item) => item.id === id);
  if (!budget || !window.confirm(`Delete the budget from ${budget.person}?`)) return;
  const budgets = state.data.budgets.filter((item) => item.id !== id);
  if (saveData({ ...state.data, budgets })) updateAll();
}

function createInitialData() {
  return {
    cycleStartDay: 1,
    categories: ["Food & dining", "Groceries", "Transport", "Bills & utilities", "Shopping", "Health", "Other"],
    accounts: ["Cash", "Debit card", "Visa credit card", "Mastercard"],
    budgets: [],
    transactions: [
      { id: "sample-groceries", date: exampleDate(3), description: "Weekly groceries", category: "Groceries", amount: 1840, account: "Debit card", paymentType: "paid", isPaid: true },
      { id: "sample-dinner", date: exampleDate(2), description: "Dinner with friends", category: "Food & dining", amount: 1260, account: "Visa credit card", paymentType: "credit", isPaid: false },
      { id: "sample-commute", date: exampleDate(1), description: "Commute", category: "Transport", amount: 360, account: "Cash", paymentType: "paid", isPaid: true },
      { id: "sample-internet", date: exampleDate(0), description: "Home internet", category: "Bills & utilities", amount: 1599, account: "Mastercard", paymentType: "credit", isPaid: true },
    ],
  };
}

function isValidData(data) {
  return Boolean(
    data &&
    (data.cycleStartDay === undefined || (Number.isInteger(data.cycleStartDay) && data.cycleStartDay >= 1 && data.cycleStartDay <= 31)) &&
    (data.budgets === undefined || (Array.isArray(data.budgets) && data.budgets.every((budget) =>
      budget &&
      typeof budget.id === "string" &&
      typeof budget.periodStart === "string" &&
      /^\d{4}-\d{2}-\d{2}$/.test(budget.periodStart) &&
      typeof budget.person === "string" &&
      Number.isFinite(budget.amount) &&
      budget.amount > 0
    ))) &&
    Array.isArray(data.categories) &&
    data.categories.every((item) => typeof item === "string") &&
    Array.isArray(data.accounts) &&
    data.accounts.every((item) => typeof item === "string") &&
    Array.isArray(data.transactions) &&
    data.transactions.every((item) =>
      item &&
      typeof item.id === "string" &&
      typeof item.date === "string" &&
      typeof item.description === "string" &&
      typeof item.category === "string" &&
      Number.isFinite(item.amount) &&
      item.amount > 0 &&
      typeof item.account === "string" &&
      (item.paymentType === "paid" || item.paymentType === "credit") &&
      (item.isPaid === undefined || typeof item.isPaid === "boolean")
    )
  );
}

function normalizeImportedData(data) {
  return {
    ...data,
    cycleStartDay: data.cycleStartDay ?? 1,
    budgets: data.budgets ?? [],
    transactions: data.transactions.map((transaction) =>
      transaction.isPaid === undefined
        ? { ...transaction, isPaid: transaction.paymentType === "paid" }
        : transaction
    ),
  };
}

function showAlert(message) {
  elements.alert.textContent = message;
  elements.alert.hidden = false;
}

function hideAlert() {
  elements.alert.textContent = "";
  elements.alert.hidden = true;
}

function setSaveStatus(message) {
  elements.saveStatus.textContent = message;
  elements.mobileSaveStatus.title = message;
  elements.mobileSaveStatus.setAttribute("aria-label", message);
  elements.mobileSaveStatus.lastChild.textContent = message === "Saving…" ? " Saving" : message === "All changes saved" ? " Saved" : " Save error";
  elements.mobileSaveStatus.classList.toggle("has-save-error", message !== "Saving…" && message !== "All changes saved");
}

function saveData(nextData) {
  setSaveStatus("Saving…");
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(nextData));
    state.data = nextData;
    setSaveStatus("All changes saved");
    hideAlert();
    return true;
  } catch (error) {
    setSaveStatus("Save failed");
    showAlert(`Could not save your changes on this device. ${error.message || "Check browser storage settings and try again."}`);
    return false;
  }
}

function loadData() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === null) {
      const initial = createInitialData();
      if (!saveData(initial)) return false;
      state.month = getCurrentCycleMonth();
      return true;
    }
    const parsed = JSON.parse(saved);
    if (!isValidData(parsed)) {
      setSaveStatus("Saved data needs attention");
      showAlert("Saved tracker data is not in a readable format. Your existing data has not been changed.");
      return false;
    }
    const hasLegacyPaymentFlags = parsed.transactions.some((item) => item.isPaid === undefined);
    const hasLegacyCycleSetting = parsed.cycleStartDay === undefined;
    const hasLegacyBudgets = parsed.budgets === undefined;
    state.data = hasLegacyPaymentFlags || hasLegacyCycleSetting || hasLegacyBudgets
      ? {
          ...parsed,
          cycleStartDay: parsed.cycleStartDay ?? 1,
          budgets: parsed.budgets ?? [],
          transactions: parsed.transactions.map((item) =>
            item.isPaid === undefined
              ? { ...item, isPaid: item.paymentType === "paid" }
              : item
          ),
        }
      : parsed;
    if ((hasLegacyPaymentFlags || hasLegacyCycleSetting || hasLegacyBudgets) && !saveData(state.data)) return false;
    setSaveStatus("All changes saved");
    state.month = getCurrentCycleMonth();
    return true;
  } catch (error) {
    setSaveStatus("Unable to read saved data");
    showAlert(`Could not read saved tracker data. ${error.message || "Check browser storage settings and reload."}`);
    return false;
  }
}

function formatAmount(amount) {
  return formatter.format(amount);
}

function getCycleStartDate(year, monthIndex) {
  const lastDay = new Date(year, monthIndex + 1, 0).getDate();
  return new Date(year, monthIndex, Math.min(state.data.cycleStartDay, lastDay));
}

function getCurrentCycleMonth() {
  const today = new Date();
  const currentStart = getCycleStartDate(today.getFullYear(), today.getMonth());
  const anchor = today >= currentStart
    ? currentStart
    : getCycleStartDate(today.getFullYear(), today.getMonth() - 1);
  return new Date(anchor.getFullYear(), anchor.getMonth(), 1);
}

function getSelectedCycle() {
  const start = getCycleStartDate(state.month.getFullYear(), state.month.getMonth());
  const nextStart = getCycleStartDate(state.month.getFullYear(), state.month.getMonth() + 1);
  const end = new Date(nextStart.getFullYear(), nextStart.getMonth(), nextStart.getDate() - 1);
  return { start, end };
}

function getMonthTransactions() {
  const { start, end } = getSelectedCycle();
  const startDate = localDateString(start);
  const endDate = localDateString(end);
  return state.data.transactions.filter((transaction) => transaction.date >= startDate && transaction.date <= endDate);
}

function getCategoryTotals(transactions) {
  const totals = new Map();
  for (const transaction of transactions) {
    totals.set(transaction.category, (totals.get(transaction.category) || 0) + transaction.amount);
  }
  return [...totals.entries()].sort((a, b) => b[1] - a[1]);
}

function renderDonut(categoryTotals, total) {
  const svg = elements.categoryDonut;
  svg.replaceChildren();
  const track = document.createElementNS("http://www.w3.org/2000/svg", "circle");
  track.setAttribute("class", "donut-track");
  track.setAttribute("cx", "80");
  track.setAttribute("cy", "80");
  track.setAttribute("r", "58");
  svg.append(track);

  const circumference = 2 * Math.PI * 58;
  let offset = 0;
  categoryTotals.forEach(([, amount], index) => {
    const segment = document.createElementNS("http://www.w3.org/2000/svg", "circle");
    const length = total > 0 ? circumference * (amount / total) : 0;
    segment.setAttribute("class", "donut-segment");
    segment.setAttribute("cx", "80");
    segment.setAttribute("cy", "80");
    segment.setAttribute("r", "58");
    segment.setAttribute("stroke", CHART_COLORS[index % CHART_COLORS.length]);
    segment.setAttribute("stroke-dasharray", `${length} ${circumference - length}`);
    segment.setAttribute("stroke-dashoffset", String(-offset));
    svg.append(segment);
    offset += length;
  });
}

function renderCategories(transactions) {
  const totals = getCategoryTotals(transactions);
  const total = totals.reduce((sum, [, amount]) => sum + amount, 0);
  elements.categoryDonut.closest(".donut-wrap").hidden = totals.length === 0;
  elements.donutTotal.textContent = compactFormatter.format(total);
  elements.categoryLegend.replaceChildren();
  elements.categoryBars.replaceChildren();
  elements.categoryEmpty.hidden = totals.length > 0;
  elements.barsEmpty.hidden = totals.length > 0;
  elements.categoryDonut.hidden = totals.length === 0;
  elements.categoryLegend.hidden = totals.length === 0;
  elements.categoryBars.hidden = totals.length === 0;

  if (!totals.length) {
    renderDonut([], 0);
    return;
  }

  renderDonut(totals, total);
  const maxAmount = totals[0][1];
  totals.forEach(([category, amount], index) => {
    const legendRow = document.createElement("div");
    legendRow.className = "legend-row";
    const swatch = document.createElement("span");
    swatch.className = "legend-swatch";
    swatch.style.backgroundColor = CHART_COLORS[index % CHART_COLORS.length];
    const name = document.createElement("button");
    name.className = "legend-name category-link";
    name.textContent = category;
    name.title = category;
    name.type = "button";
    name.addEventListener("click", () => openCategoryTransactions(category));
    const value = document.createElement("span");
    value.className = "legend-amount";
    value.textContent = formatAmount(amount);
    legendRow.append(swatch, name, value);
    elements.categoryLegend.append(legendRow);

    const barRow = document.createElement("div");
    barRow.className = "bar-row";
    const barName = document.createElement("button");
    barName.className = "bar-name category-link";
    barName.textContent = category;
    barName.title = category;
    barName.type = "button";
    barName.addEventListener("click", () => openCategoryTransactions(category));
    const track = document.createElement("div");
    track.className = "bar-track";
    track.setAttribute("role", "img");
    track.setAttribute("aria-label", `${category}: ${formatAmount(amount)}`);
    const fill = document.createElement("div");
    fill.className = "bar-fill";
    fill.style.width = `${Math.max(2, (amount / maxAmount) * 100)}%`;
    track.append(fill);
    const barAmount = document.createElement("span");
    barAmount.className = "bar-amount";
    barAmount.textContent = formatAmount(amount);
    barRow.append(barName, track, barAmount);
    elements.categoryBars.append(barRow);
  });
}

function renderAccountBars(transactions) {
  const totals = new Map();
  for (const transaction of transactions) {
    totals.set(transaction.account, (totals.get(transaction.account) || 0) + transaction.amount);
  }
  const accounts = [...totals.entries()].sort((a, b) => b[1] - a[1]);
  elements.accountBars.replaceChildren();
  elements.accountBars.hidden = accounts.length === 0;
  elements.accountsEmpty.hidden = accounts.length > 0;
  const maxAmount = accounts.length ? accounts[0][1] : 0;
  accounts.forEach(([account, amount], index) => {
    const row = document.createElement("div");
    row.className = "bar-row";
    const name = document.createElement("span");
    name.className = "bar-name";
    name.textContent = account;
    name.title = account;
    const track = document.createElement("div");
    track.className = "bar-track";
    track.setAttribute("role", "img");
    track.setAttribute("aria-label", `${account}: ${formatAmount(amount)}`);
    const fill = document.createElement("div");
    fill.className = "bar-fill";
    if (index % 2 === 1) fill.classList.add("bar-fill-alternate");
    fill.style.width = `${Math.max(2, (amount / maxAmount) * 100)}%`;
    track.append(fill);
    const value = document.createElement("span");
    value.className = "bar-amount";
    value.textContent = formatAmount(amount);
    row.append(name, track, value);
    elements.accountBars.append(row);
  });
}

function openCategoryTransactions(category) {
  const transactions = getMonthTransactions()
    .filter((transaction) => transaction.category === category)
    .sort((a, b) => b.date.localeCompare(a.date));
  elements.categoryTransactionsTitle.textContent = category;
  elements.categoryTransactionsList.replaceChildren();
  for (const transaction of transactions) {
    const item = document.createElement("article");
    item.className = "transaction-item";
    const primary = document.createElement("div");
    primary.className = "item-primary";
    const mark = document.createElement("span");
    mark.className = "item-mark";
    mark.textContent = "↗";
    const copy = document.createElement("div");
    copy.className = "item-copy";
    const description = document.createElement("strong");
    description.textContent = transaction.description;
    const metadata = document.createElement("span");
    metadata.textContent = `${dateFormatter.format(new Date(`${transaction.date}T12:00:00`))} · ${transaction.account}`;
    copy.append(description, metadata);
    primary.append(mark, copy);
    const amount = document.createElement("span");
    amount.className = "item-amount";
    amount.textContent = formatAmount(transaction.amount);
    item.append(primary, amount);
    elements.categoryTransactionsList.append(item);
  }
  elements.categoryTransactionsDialog.showModal();
}

function renderOverview() {
  const transactions = getMonthTransactions();
  const creditTransactions = transactions.filter((item) => item.paymentType === "credit");
  const paidTransactions = transactions.filter((item) => item.paymentType === "paid");
  const total = transactions.reduce((sum, item) => sum + item.amount, 0);
  const creditTotal = creditTransactions.reduce((sum, item) => sum + item.amount, 0);
  const paidTotal = paidTransactions.reduce((sum, item) => sum + item.amount, 0);
  const { start, end } = getSelectedCycle();
  elements.currentMonthLabel.textContent = `${dateFormatter.format(start)} – ${dateFormatter.format(end)}`;
  elements.totalUsed.textContent = formatAmount(total);
  elements.creditUsed.textContent = formatAmount(creditTotal);
  elements.paidUsed.textContent = formatAmount(paidTotal);
  elements.totalBudget.textContent = formatAmount(getPeriodBudgets().reduce((sum, budget) => sum + budget.amount, 0));
  const pendingCredit = creditTransactions.filter((item) => !item.isPaid).length;
  elements.creditUsedCaption.textContent = `${pendingCredit} still pending · all credit purchases`;
  renderCategories(transactions);
  renderAccountBars(transactions);
}

function createEmptyState(title, message, buttonLabel, action) {
  const wrapper = document.createElement("div");
  wrapper.className = "empty-state";
  const mark = document.createElement("span");
  mark.className = "empty-state-mark";
  mark.textContent = "✓";
  const heading = document.createElement("h2");
  heading.textContent = title;
  const description = document.createElement("p");
  description.textContent = message;
  wrapper.append(mark, heading, description);
  if (buttonLabel && action) {
    const button = document.createElement("button");
    button.className = "button button-quiet";
    button.type = "button";
    button.textContent = buttonLabel;
    button.style.marginTop = "14px";
    button.addEventListener("click", action);
    wrapper.append(button);
  }
  return wrapper;
}

function renderPending() {
  const creditTransactions = state.data.transactions
    .filter((transaction) => transaction.paymentType === "credit")
    .sort((a, b) => b.date.localeCompare(a.date));
  const pending = creditTransactions.filter((transaction) => !transaction.isPaid);
  const paid = creditTransactions.filter((transaction) => transaction.isPaid);
  const total = pending.reduce((sum, item) => sum + item.amount, 0);
  elements.pendingTotal.textContent = formatAmount(total);
  elements.pendingCount.textContent = String(pending.length);
  elements.pendingNavCount.textContent = String(pending.length);
  elements.pendingNavCount.hidden = pending.length === 0;
  elements.pendingList.replaceChildren();
  elements.paidCreditList.replaceChildren();

  if (!creditTransactions.length) {
    elements.pendingList.append(createEmptyState("No credit transactions yet", "Credit transactions will appear here until you mark them paid.", "Add a transaction", openNewTransaction));
    document.querySelector(".paid-credit-group").hidden = true;
    return;
  }
  document.querySelector(".paid-credit-group").hidden = paid.length === 0;
  if (!pending.length) {
    elements.pendingList.append(createEmptyState("You’re all caught up", "Any unpaid credit transactions will show up here.", null, null));
  }

  pending.forEach((transaction) => elements.pendingList.append(createCreditPaymentItem(transaction)));
  paid.forEach((transaction) => elements.paidCreditList.append(createCreditPaymentItem(transaction)));
}

function createCreditPaymentItem(transaction) {
  const item = document.createElement("article");
  item.className = "pending-item";
  const primary = document.createElement("div");
  primary.className = "item-primary";
  const mark = document.createElement("span");
  mark.className = "item-mark";
  mark.textContent = "↗";
  const copy = document.createElement("div");
  copy.className = "item-copy";
  const title = document.createElement("strong");
  title.textContent = transaction.description;
  const metadata = document.createElement("span");
  metadata.textContent = `${dateFormatter.format(new Date(`${transaction.date}T12:00:00`))} · ${transaction.account} · ${transaction.category}`;
  copy.append(title, metadata);
  primary.append(mark, copy);
  const actions = document.createElement("div");
  actions.className = "item-actions";
  const amount = document.createElement("span");
  amount.className = "item-amount";
  amount.textContent = formatAmount(transaction.amount);
  const paidButton = document.createElement("button");
  paidButton.className = transaction.isPaid ? "button button-quiet mark-paid-button" : "button button-primary mark-paid-button";
  paidButton.type = "button";
  paidButton.textContent = transaction.isPaid ? "Mark as unpaid" : "Mark as paid";
  paidButton.addEventListener("click", () => setCreditPaidState(transaction.id, !transaction.isPaid));
  actions.append(amount, paidButton);
  item.append(primary, actions);
  return item;
}

function renderTransactionList() {
  const transactions = [...state.data.transactions].sort((a, b) => b.date.localeCompare(a.date));
  elements.transactionList.replaceChildren();
  if (!transactions.length) {
    elements.transactionList.append(createEmptyState("No transactions yet", "Add a transaction to start building your money tracker.", "Add transaction", openNewTransaction));
    return;
  }

  for (const transaction of transactions) {
    const item = document.createElement("article");
    item.className = "transaction-item";
    const primary = document.createElement("div");
    primary.className = "item-primary";
    const mark = document.createElement("span");
    mark.className = "item-mark";
    mark.textContent = transaction.paymentType === "credit" ? "↗" : "✓";
    const copy = document.createElement("div");
    copy.className = "item-copy";
    const title = document.createElement("strong");
    title.textContent = transaction.description;
    const metadata = document.createElement("span");
    metadata.textContent = `${dateFormatter.format(new Date(`${transaction.date}T12:00:00`))} · ${transaction.category} · ${transaction.account}`;
    copy.append(title, metadata);
    primary.append(mark, copy);
    const actions = document.createElement("div");
    actions.className = "transaction-actions";
    const amount = document.createElement("span");
    amount.className = "item-amount";
    amount.textContent = formatAmount(transaction.amount);
    const editButton = document.createElement("button");
    editButton.className = "small-action";
    editButton.type = "button";
    editButton.textContent = "Edit";
    editButton.addEventListener("click", () => openEditTransaction(transaction.id));
    const deleteButton = document.createElement("button");
    deleteButton.className = "small-action is-danger";
    deleteButton.type = "button";
    deleteButton.textContent = "Delete";
    deleteButton.addEventListener("click", () => deleteTransaction(transaction.id));
    actions.append(amount, editButton, deleteButton);
    item.append(primary, actions);
    elements.transactionList.append(item);
  }
}

function appendOption(select, value, label) {
  const option = document.createElement("option");
  option.value = value;
  option.textContent = label;
  select.append(option);
}

function fillSelects(transaction) {
  elements.transactionCategory.replaceChildren();
  elements.transactionAccount.replaceChildren();
  state.data.categories.forEach((category) => appendOption(elements.transactionCategory, category, category));
  state.data.accounts.forEach((account) => appendOption(elements.transactionAccount, account, account));
  if (transaction && !state.data.categories.includes(transaction.category)) {
    appendOption(elements.transactionCategory, transaction.category, `${transaction.category} (saved)`);
  }
  if (transaction && !state.data.accounts.includes(transaction.account)) {
    appendOption(elements.transactionAccount, transaction.account, `${transaction.account} (saved)`);
  }
  elements.transactionCategory.disabled = state.data.categories.length === 0;
  elements.transactionAccount.disabled = state.data.accounts.length === 0;
  elements.saveTransactionButton.disabled = state.data.categories.length === 0 || state.data.accounts.length === 0;
  if (state.data.categories.length === 0 || state.data.accounts.length === 0) {
    showAlert("Add at least one category and one account before saving a transaction.");
  }
}

function openNewTransaction() {
  elements.transactionForm.reset();
  elements.transactionId.value = "";
  elements.transactionDialogTitle.textContent = "Add transaction";
  elements.saveTransactionButton.textContent = "Save transaction";
  elements.transactionDate.value = localDateString(new Date());
  fillSelects(null);
  elements.transactionDialog.showModal();
}

function openEditTransaction(id) {
  const transaction = state.data.transactions.find((item) => item.id === id);
  if (!transaction) return;
  elements.transactionForm.reset();
  elements.transactionId.value = transaction.id;
  elements.transactionDescription.value = transaction.description;
  elements.transactionDate.value = transaction.date;
  elements.transactionAmount.value = String(transaction.amount);
  elements.transactionDialogTitle.textContent = "Edit transaction";
  elements.saveTransactionButton.textContent = "Save changes";
  fillSelects(transaction);
  elements.transactionCategory.value = transaction.category;
  elements.transactionAccount.value = transaction.account;
  elements.transactionForm.elements.paymentType.value = transaction.paymentType;
  elements.transactionDialog.showModal();
}

function updateAll() {
  renderOverview();
  renderPending();
  renderTransactionList();
  renderBudgets();
  renderManageLists();
}

function submitTransaction(event) {
  event.preventDefault();
  const form = new FormData(elements.transactionForm);
  const amount = Number(form.get("amount"));
  const description = String(form.get("description") || "").trim();
  const date = String(form.get("date") || "");
  const category = String(form.get("category") || "");
  const account = String(form.get("account") || "");
  const paymentType = String(form.get("paymentType") || "");
  if (!description || !date || !category || !account || !Number.isFinite(amount) || amount <= 0 || !["paid", "credit"].includes(paymentType)) {
    showAlert("Check the transaction details and enter a valid amount greater than zero.");
    return;
  }
  const id = elements.transactionId.value;
  const existing = state.data.transactions.find((item) => item.id === id);
  const transaction = {
    id: id || `txn-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    date,
    description,
    category,
    amount,
    account,
    paymentType,
    isPaid: paymentType === "paid" || Boolean(existing && existing.paymentType === "credit" && existing.isPaid),
  };
  const transactions = existing
    ? state.data.transactions.map((item) => item.id === id ? transaction : item)
    : [...state.data.transactions, transaction];
  if (!saveData({ ...state.data, transactions })) return;
  elements.transactionDialog.close();
  updateAll();
}

function setCreditPaidState(id, isPaid) {
  const transactions = state.data.transactions.map((transaction) =>
    transaction.id === id && transaction.paymentType === "credit"
      ? { ...transaction, isPaid }
      : transaction
  );
  if (saveData({ ...state.data, transactions })) updateAll();
}

function deleteTransaction(id) {
  const transaction = state.data.transactions.find((item) => item.id === id);
  if (!transaction || !window.confirm(`Delete "${transaction.description}"?`)) return;
  const transactions = state.data.transactions.filter((item) => item.id !== id);
  if (saveData({ ...state.data, transactions })) updateAll();
}

function renderManageLists() {
  renderCycleStartDayOptions();
  renderManageList(elements.categoryList, state.data.categories, "category");
  renderManageList(elements.accountList, state.data.accounts, "account");
}

function ordinal(value) {
  const remainder = value % 100;
  if (remainder >= 11 && remainder <= 13) return `${value}th`;
  switch (value % 10) {
    case 1: return `${value}st`;
    case 2: return `${value}nd`;
    case 3: return `${value}rd`;
    default: return `${value}th`;
  }
}

function renderCycleStartDayOptions() {
  elements.cycleStartDay.replaceChildren();
  for (let day = 1; day <= 31; day += 1) {
    const option = document.createElement("option");
    option.value = String(day);
    option.textContent = day === 31
      ? "31st (last day in shorter months)"
      : `${ordinal(day)} of each month`;
    elements.cycleStartDay.append(option);
  }
  elements.cycleStartDay.value = String(state.data.cycleStartDay);
}

function updateCycleStartDay() {
  const previousStartDay = state.data.cycleStartDay;
  const cycleStartDay = Number(elements.cycleStartDay.value);
  if (!Number.isInteger(cycleStartDay) || cycleStartDay < 1 || cycleStartDay > 31) {
    showAlert("Choose a valid cycle start day from 1 to 31.");
    renderCycleStartDayOptions();
    return;
  }
  if (!saveData({ ...state.data, cycleStartDay })) {
    elements.cycleStartDay.value = String(previousStartDay);
    return;
  }
  state.month = getCurrentCycleMonth();
  updateAll();
}

function renderManageList(list, values, type) {
  list.replaceChildren();
  if (!values.length) {
    const empty = document.createElement("li");
    empty.textContent = `No ${type}s yet`;
    list.append(empty);
    return;
  }
  values.forEach((value) => {
    const row = document.createElement("li");
    const name = document.createElement("span");
    name.textContent = value;
    const remove = document.createElement("button");
    remove.className = "remove-option";
    remove.type = "button";
    remove.textContent = "×";
    remove.setAttribute("aria-label", `Remove ${value}`);
    remove.addEventListener("click", () => removeOption(type, value));
    row.append(name, remove);
    list.append(row);
  });
}

function addOption(type, rawValue) {
  const value = rawValue.trim();
  if (!value) return;
  const property = type === "category" ? "categories" : "accounts";
  if (state.data[property].some((item) => item.toLocaleLowerCase() === value.toLocaleLowerCase())) {
    showAlert(`That ${type} already exists.`);
    return;
  }
  const sortedValues = [...state.data[property], value]
    .sort((first, second) => first.localeCompare(second, undefined, { sensitivity: "base" }));
  const nextData = { ...state.data, [property]: sortedValues };
  if (!saveData(nextData)) return;
  renderManageLists();
}

function removeOption(type, value) {
  const property = type === "category" ? "categories" : "accounts";
  const nextData = { ...state.data, [property]: state.data[property].filter((item) => item !== value) };
  if (!saveData(nextData)) return;
  renderManageLists();
}

function exportData() {
  const backup = {
    format: "ledger-monthly-tracker",
    version: 1,
    exportedAt: new Date().toISOString(),
    data: state.data,
  };
  const blob = new Blob([JSON.stringify(backup, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `ledger-backup-${localDateString(new Date())}.json`;
  document.body.append(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

async function importData(file) {
  try {
    const parsed = JSON.parse(await file.text());
    const imported = parsed && parsed.format === "ledger-monthly-tracker" && parsed.version === 1
      ? parsed.data
      : parsed;
    if (!isValidData(imported)) {
      throw new Error("This file does not contain a valid Ledger backup.");
    }
    if (!window.confirm("Importing this backup will replace all tracker data saved in this browser. This cannot be undone. Continue?")) return;
    const normalizedData = normalizeImportedData(imported);
    if (!saveData(normalizedData)) return;
    state.month = getCurrentCycleMonth();
    updateAll();
    showAlert("Backup imported. This browser now has the data from the selected file.");
  } catch (error) {
    showAlert(`Could not import backup. ${error.message || "Choose a valid Ledger JSON backup file."}`);
  }
}

function setPage(page) {
  state.page = page;
  const pages = {
    overview: elements.overviewPage,
    pending: elements.pendingPage,
    transactions: elements.transactionsPage,
    budgets: elements.budgetsPage,
  };
  Object.entries(pages).forEach(([pageName, pageElement]) => {
    const isCurrentPage = pageName === page;
    pageElement.hidden = !isCurrentPage;
    pageElement.classList.toggle("is-visible", isCurrentPage);
  });
  document.querySelectorAll(".nav-link").forEach((button) => {
    const active = button.dataset.page === page;
    button.classList.toggle("is-active", active);
    button.setAttribute("aria-current", active ? "page" : "false");
  });
  if (page === "overview") renderOverview();
  else if (page === "pending") renderPending();
  else if (page === "transactions") renderTransactionList();
  else if (page === "budgets") renderBudgets();
}

document.querySelectorAll(".nav-link").forEach((button) => {
  button.addEventListener("click", () => setPage(button.dataset.page));
});
document.getElementById("previous-month").addEventListener("click", () => {
  state.month = new Date(state.month.getFullYear(), state.month.getMonth() - 1, 1);
  renderOverview();
});
document.getElementById("next-month").addEventListener("click", () => {
  state.month = new Date(state.month.getFullYear(), state.month.getMonth() + 1, 1);
  renderOverview();
});
document.getElementById("add-transaction-button").addEventListener("click", openNewTransaction);
function openManageDialog() {
  elements.manageDialog.showModal();
}

document.getElementById("manage-data-button").addEventListener("click", openManageDialog);
document.getElementById("add-budget-button").addEventListener("click", openNewBudget);
document.getElementById("export-data-button").addEventListener("click", exportData);
document.getElementById("import-data-button").addEventListener("click", () => document.getElementById("import-data-file").click());
document.getElementById("import-data-file").addEventListener("change", (event) => {
  const [file] = event.currentTarget.files;
  if (file) importData(file);
  event.currentTarget.value = "";
});
elements.cycleStartDay.addEventListener("change", updateCycleStartDay);
elements.budgetForm.addEventListener("submit", submitBudget);
document.getElementById("previous-budget-period").addEventListener("click", () => {
  state.month = new Date(state.month.getFullYear(), state.month.getMonth() - 1, 1);
  renderBudgets();
});
document.getElementById("next-budget-period").addEventListener("click", () => {
  state.month = new Date(state.month.getFullYear(), state.month.getMonth() + 1, 1);
  renderBudgets();
});
elements.transactionForm.addEventListener("submit", submitTransaction);
document.getElementById("add-category-form").addEventListener("submit", (event) => {
  event.preventDefault();
  const input = document.getElementById("new-category");
  addOption("category", input.value);
  if (!elements.alert.hidden && elements.alert.textContent.includes("already exists")) return;
  input.value = "";
});
document.getElementById("add-account-form").addEventListener("submit", (event) => {
  event.preventDefault();
  const input = document.getElementById("new-account");
  addOption("account", input.value);
  if (!elements.alert.hidden && elements.alert.textContent.includes("already exists")) return;
  input.value = "";
});
document.querySelectorAll("[data-close]").forEach((button) => {
  button.addEventListener("click", () => document.getElementById(button.dataset.close).close());
});

if (loadData()) {
  updateAll();
  setPage("overview");
}
