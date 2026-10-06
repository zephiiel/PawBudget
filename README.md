# Ledger

A minimalist monthly money tracker that runs entirely in your browser. No account, server, network connection, or third-party library is required. Transactions, budgets, categories, and accounts are saved automatically in browser storage on the current device. The app displays a save confirmation after each change; no manual save button is needed.

## Run offline

Open `index.html` in a modern browser. If that browser restricts storage for local files, serve this folder from a local-only web server and open its local address:

```powershell
python -m http.server 8000
```

Then visit `http://localhost:8000`. The app itself makes no network requests; keep the server bound to your own computer and stop it when finished.

The first launch seeds a small example set so the dashboard is immediately visible. You can edit or delete those transactions and manage the category and account choices from the top bar.

## Using the tracker

- Use the period arrows to view paid and credit spending and category graphs for a billing period.
- Click a category in the spending charts to see its transactions for the selected billing period. The overview also charts spending by account.
- Open **Settings & lists** to choose the monthly billing-cycle start day (1st–31st). The next period starts on the same day next month, and the selected dashboard period runs through the day before it. For short months, a start day that does not exist falls on that month's last day.
- Add a transaction with its date, description, category, amount, account, and payment type.
- New credit transactions start as pending. Open **Pending payments** to mark one as paid or unpaid; paid credit transactions are listed below unpaid ones.
- Open **All transactions** in the sidebar to review, edit, or delete transaction records.
- Add a budget with a person's name and amount. Budgets apply only to the selected billing period; review, edit, or delete them on the **Budgets** sidebar page. The monthly overview shows the selected period's total budget.
- Open **Settings & lists** to add or remove categories and accounts and set the billing-cycle start day. Removing a category or account does not change names saved on existing transactions.
- Use **Export backup** in settings to download a JSON backup. To transfer entries to another browser, open the tracker there, choose **Import backup**, select the JSON file, and confirm replacement. Browser data is separate for each browser/profile and may also be separate between `file://` and local server addresses.
- If Chrome appears empty, open the tracker in the browser/profile where the entries are visible, export a backup there, then open the same app in Chrome, import that file, and confirm. The app intentionally does not sync data between browsers.

Browser storage is local to this browser profile. Clearing site data or using a different browser/device will not carry the tracker data over.
