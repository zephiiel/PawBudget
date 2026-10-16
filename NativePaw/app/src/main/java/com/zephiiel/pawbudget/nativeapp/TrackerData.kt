package com.zephiiel.pawbudget.nativeapp

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

data class Transaction(
    val id: String,
    val date: String,
    val description: String,
    val category: String,
    val amount: Double,
    val account: String,
    val paymentType: String,
    val isPaid: Boolean,
    val createdAt: Long = 0L,
)

data class Budget(
    val id: String,
    val periodStart: String,
    val person: String,
    val amount: Double,
)

data class PeriodTodo(
    val id: String,
    val title: String,
    val completed: Boolean,
    val periodStart: String,
    val amount: Double? = null,
)

data class TrackerData(
    val cycleStartDay: Int = 1,
    val categories: List<String> = defaultCategories,
    val accounts: List<String> = defaultAccounts,
    val transactions: List<Transaction> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val todos: List<PeriodTodo> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("cycleStartDay", cycleStartDay)
        put("categories", JSONArray(categories))
        put("accounts", JSONArray(accounts))
        put("transactions", JSONArray().apply {
            transactions.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("date", item.date)
                    put("description", item.description)
                    put("category", item.category)
                    put("amount", item.amount)
                    put("account", item.account)
                    put("paymentType", item.paymentType)
                    put("isPaid", item.isPaid)
                    if (item.createdAt > 0L) put("createdAt", item.createdAt)
                })
            }
        })
        put("budgets", JSONArray().apply {
            budgets.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("periodStart", item.periodStart)
                    put("person", item.person)
                    put("amount", item.amount)
                })
            }
        })
        put("todos", JSONArray().apply {
            todos.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("completed", item.completed)
                    put("periodStart", item.periodStart)
                    item.amount?.let { put("amount", it) }
                })
            }
        })
    }

    companion object {
        val defaultCategories = listOf(
            "Food & dining", "Groceries", "Transport", "Bills & utilities",
            "Shopping", "Health", "Other",
        )
        val defaultAccounts = listOf("Cash", "Debit card", "Visa credit card", "Mastercard")

        fun initial(today: LocalDate = LocalDate.now()): TrackerData {
            return TrackerData(
                transactions = listOf(
                    Transaction("sample-groceries", today.minusDays(3).toString(), "Weekly groceries", "Groceries", 1840.0, "Debit card", "paid", true),
                    Transaction("sample-dinner", today.minusDays(2).toString(), "Dinner with friends", "Food & dining", 1260.0, "Visa credit card", "credit", false),
                    Transaction("sample-commute", today.minusDays(1).toString(), "Commute", "Transport", 360.0, "Cash", "paid", true),
                    Transaction("sample-internet", today.toString(), "Home internet", "Bills & utilities", 1599.0, "Mastercard", "credit", true),
                ),
            )
        }

        fun fromBackup(root: JSONObject): TrackerData {
            val data = if (
                root.optString("format") == "ledger-monthly-tracker" &&
                root.optInt("version") == 1
            ) root.optJSONObject("data")
                ?: throw IllegalArgumentException("The backup does not contain tracker data.")
            else root

            val day = if (data.has("cycleStartDay")) data.optInt("cycleStartDay", -1) else 1
            require(day in 1..31) { "The billing-cycle start day must be from 1 to 31." }
            val categories = data.requiredStringArray("categories")
            val accounts = data.requiredStringArray("accounts")
            val transactionArray = data.requiredArray("transactions")
            val transactions = List(transactionArray.length()) { index ->
                val item = transactionArray.optJSONObject(index) ?: invalidBackup()
                val paymentType = item.getString("paymentType")
                require(paymentType == "paid" || paymentType == "credit") { "A transaction has an unsupported payment type." }
                val amount = item.getDouble("amount")
                require(amount.isFinite() && amount > 0.0) { "Transaction amounts must be greater than zero." }
                val date = item.getString("date")
                LocalDate.parse(date)
                Transaction(
                    id = item.getString("id"),
                    date = date,
                    description = item.getString("description"),
                    category = item.getString("category"),
                    amount = amount,
                    account = item.getString("account"),
                    paymentType = paymentType,
                    isPaid = item.optBoolean("isPaid", paymentType == "paid"),
                    createdAt = item.optLong("createdAt", 0L),
                )
            }
            val budgets = data.optJSONArray("budgets").toObjectList { item ->
                val period = item.getString("periodStart")
                LocalDate.parse(period)
                val amount = item.getDouble("amount")
                require(amount.isFinite() && amount > 0.0) { "Budget amounts must be greater than zero." }
                Budget(item.getString("id"), period, item.getString("person"), amount)
            }
            val currentPeriodStart = cycleStartDate(cycleMonth(LocalDate.now(), day), day).toString()
            val todos = data.optJSONArray("todos").toObjectList { item ->
                val title = item.getString("title").trim()
                require(title.isNotEmpty()) { "To-do titles cannot be empty." }
                val period = item.optString("periodStart", currentPeriodStart)
                LocalDate.parse(period)
                val amount = if (item.has("amount") && !item.isNull("amount")) item.getDouble("amount") else null
                require(amount == null || (amount.isFinite() && amount > 0.0)) {
                    "To-do amounts must be greater than zero."
                }
                PeriodTodo(item.getString("id"), title, item.optBoolean("completed"), period, amount)
            }
            return TrackerData(day, categories, accounts, transactions, budgets, todos)
        }

        private fun JSONObject.requiredStringArray(key: String): List<String> {
            val array = requiredArray(key)
            return List(array.length()) { index ->
                array.opt(index) as? String ?: invalidBackup()
            }
        }

        private fun JSONObject.requiredArray(key: String): JSONArray =
            optJSONArray(key) ?: invalidBackup()

        private inline fun <T> JSONArray?.toObjectList(transform: (JSONObject) -> T): List<T> {
            val array = this ?: return emptyList()
            return List(array.length()) { index ->
                transform(array.optJSONObject(index) ?: invalidBackup())
            }
        }

        private fun invalidBackup(): Nothing =
            throw IllegalArgumentException("This file does not contain a valid PawBudget backup.")
    }
}

fun cycleStartDate(month: YearMonth, day: Int): LocalDate =
    month.atDay(day.coerceAtMost(month.lengthOfMonth()))

fun cycleMonth(date: LocalDate, day: Int): YearMonth {
    val currentStart = cycleStartDate(YearMonth.from(date), day)
    return if (date >= currentStart) YearMonth.from(date) else YearMonth.from(date).minusMonths(1)
}

fun periodStartFor(date: LocalDate, day: Int): LocalDate =
    cycleStartDate(cycleMonth(date, day), day)

fun periodEnd(month: YearMonth, day: Int): LocalDate =
    cycleStartDate(month.plusMonths(1), day).minusDays(1)

fun periodStartLabel(month: YearMonth, day: Int): String =
    cycleStartDate(month, day).toString()

fun budgetAfterSelectedTodos(remainingBudget: Double, selectedTodos: List<PeriodTodo>): Double =
    remainingBudget - selectedTodos.sumOf { it.amount ?: 0.0 }
