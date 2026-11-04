package com.zephiiel.pawbudget.nativeapp

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class TrackerDataTest {
    @Test
    fun readsExistingWrappedPawBudgetBackupAndKeepsPaymentState() {
        val backup = JSONObject(
            """
            {
              "format": "ledger-monthly-tracker",
              "version": 1,
              "data": {
                "cycleStartDay": 15,
                "categories": ["Food & dining"],
                "accounts": ["Cash"],
                "transactions": [{
                  "id": "txn-1",
                  "date": "2026-10-16",
                  "description": "Dinner",
                  "category": "Food & dining",
                  "amount": 125.5,
                  "account": "Cash",
                  "paymentType": "credit",
                  "isPaid": false
                }],
                "budgets": [{
                  "id": "budget-1",
                  "periodStart": "2026-10-15",
                  "person": "Alex",
                  "amount": 500
                }],
                "todos": [{
                  "id": "todo-1",
                  "title": "Pay bill",
                  "completed": true,
                  "periodStart": "2026-10-15",
                  "amount": 125.5
                }]
              }
            }
            """.trimIndent(),
        )

        val data = TrackerData.fromBackup(backup)

        assertEquals(15, data.cycleStartDay)
        assertEquals(emptyList<String>(), data.excludedAccounts)
        assertEquals(false, data.transactions.single().isPaid)
        assertEquals(125.5, data.transactions.single().amount, 0.0)
        assertEquals("Alex", data.budgets.single().person)
        assertTrue(data.todos.single().completed)
        assertEquals(125.5, data.todos.single().amount!!, 0.0)
        assertEquals(data, TrackerData.fromBackup(data.toJson()))
    }

    @Test
    fun normalizesLegacyRecordsAndUnwrappedData() {
        val legacy = JSONObject(
            """
            {
              "categories": ["Other"],
              "accounts": ["Cash"],
              "transactions": [{
                "id": "txn-old",
                "date": "2026-10-17",
                "description": "Legacy payment",
                "category": "Other",
                "amount": 10,
                "account": "Cash",
                "paymentType": "paid"
              }],
              "todos": [{
                "id": "todo-old",
                "title": "Remember",
                "completed": false
              }]
            }
            """.trimIndent(),
        )

        val data = TrackerData.fromBackup(legacy)

        assertTrue(data.transactions.single().isPaid)
        assertEquals(1, data.cycleStartDay)
        assertEquals(cycleStartDate(cycleMonth(LocalDate.now(), 1), 1).toString(), data.todos.single().periodStart)
    }

    @Test
    fun billingPeriodsClampToShortMonthsAndRespectStartBoundary() {
        assertEquals(LocalDate.of(2026, 2, 28), cycleStartDate(YearMonth.of(2026, 2), 31))
        assertEquals(YearMonth.of(2026, 2), cycleMonth(LocalDate.of(2026, 3, 30), 31))
        assertEquals(YearMonth.of(2026, 3), cycleMonth(LocalDate.of(2026, 3, 31), 31))
        assertEquals(LocalDate.of(2026, 2, 27), periodEnd(YearMonth.of(2026, 1), 31))
    }

    @Test
    fun movedTransactionsKeepTheirDateAndAssignedBillingPeriodInBackups() {
        val transaction = Transaction(
            id = "txn-moved",
            date = "2026-10-14",
            description = "After cutoff",
            category = "Other",
            amount = 100.0,
            account = "Cash",
            paymentType = "credit",
            isPaid = false,
            billingPeriodStart = "2026-10-15",
        )
        val data = TrackerData(transactions = listOf(transaction))

        assertEquals("2026-09-15", transactionPeriodStart(transaction, 15))
        assertEquals("2026-10-15", transactionStatementPeriodStart(transaction, 15))
        assertEquals(listOf(transaction), transactionsForPeriod(data.transactions, YearMonth.of(2026, 9), 15))
        assertTrue(transactionsForPeriod(data.transactions, YearMonth.of(2026, 10), 15).isEmpty())
        assertTrue(transactionsForStatementPeriod(data.transactions, YearMonth.of(2026, 9), 15).isEmpty())
        assertEquals(listOf(transaction), transactionsForStatementPeriod(data.transactions, YearMonth.of(2026, 10), 15))
        assertEquals(
            400.0,
            budgetRemaining(500.0, transactionsForPeriod(data.transactions, YearMonth.of(2026, 9), 15), emptySet()),
            0.0,
        )
        assertEquals(transaction, TrackerData.fromBackup(data.toJson()).transactions.single())
    }

    @Test
    fun releaseVersionComparisonHandlesOptionalPatchComponents() {
        assertTrue(compareVersions(listOf(1, 2, 1), listOf(1, 2, 0)) > 0)
        assertEquals(0, compareVersions(listOf(1, 2), listOf(1, 2, 0)))
        assertTrue(compareVersions(listOf(2, 0, 0), listOf(1, 9, 9)) > 0)
    }

    @Test
    fun nativeUpdaterRecognizesPublishedPawBudgetReleaseAssetNames() {
        assertTrue(isNativeUpdateApkAsset("PawBudget.v2.1.apk", "v2.1"))
        assertTrue(isNativeUpdateApkAsset("NativePaw-2.1.apk", "v2.1"))
        assertTrue(isNativeUpdateApkAsset("NativePaw-2.2.apk", "v2.2"))
        assertFalse(isNativeUpdateApkAsset("app-debug.apk", "v2.1"))
        assertFalse(isNativeUpdateApkAsset("PawBudget.v2.0.apk", "v2.1"))
    }

    @Test
    fun toDoAmountsAreOptionalAndPreservedInBackups() {
        val legacy = JSONObject(
            """
            {
              "categories": [],
              "accounts": [],
              "transactions": [],
              "todos": [{
                "id": "todo-no-amount",
                "title": "Remember",
                "completed": false
              }]
            }
            """.trimIndent(),
        )

        val data = TrackerData.fromBackup(legacy)

        assertEquals(null, data.todos.single().amount)
        assertEquals(data, TrackerData.fromBackup(data.toJson()))
    }

    @Test
    fun selectedTaskAmountsReduceRemainingBudgetAndMissingAmountsCountAsZero() {
        val selected = listOf(
            PeriodTodo("todo-1", "Task with amount", false, "2026-10-01", 120.0),
            PeriodTodo("todo-2", "Task without amount", false, "2026-10-01"),
            PeriodTodo("todo-3", "Another task with amount", false, "2026-10-01", 30.0),
        )

        assertEquals(850.0, budgetAfterSelectedTodos(1000.0, selected), 0.0)
    }

    @Test
    fun legacyAccountExclusionsAreCombinedAndPersistedAsOneSharedSetting() {
        val transaction = Transaction("txn-1", "2026-10-17", "Purchase", "Other", 125.0, "Cash", "paid", true)
        val data = TrackerData(
            accounts = listOf("Cash", "Card"),
            transactions = listOf(transaction),
            excludedAccounts = listOf("Card", "Cash"),
        )

        assertEquals(data, TrackerData.fromBackup(data.toJson()))
        assertEquals(500.0, budgetRemaining(500.0, listOf(transaction), data.excludedAccounts.toSet()), 0.0)
        assertEquals(0.0, totalUsed(listOf(transaction), data.excludedAccounts.toSet()), 0.0)
        assertTrue(transactionsExcludingAccounts(listOf(transaction), data.excludedAccounts.toSet()).isEmpty())
    }

    @Test
    fun legacyBudgetAndTotalUsedExclusionsMigrateAsTheirUnion() {
        val legacyBackup = JSONObject(
            """
            {
              "categories": [],
              "accounts": ["Cash", "Card"],
              "transactions": [],
              "excludedBudgetAccounts": ["Cash"],
              "excludedTotalUsedAccounts": ["Card"]
            }
            """.trimIndent(),
        )

        val data = TrackerData.fromBackup(legacyBackup)
        assertEquals(listOf("Card", "Cash"), data.excludedAccounts)
        val serialized = data.toJson()
        assertEquals(listOf("Card", "Cash"), serialized.getJSONArray("excludedAccounts").let { array ->
            List(array.length()) { index -> array.getString(index) }
        })
        assertEquals(listOf("Card", "Cash"), serialized.getJSONArray("excludedBudgetAccounts").let { array ->
            List(array.length()) { index -> array.getString(index) }
        })
        assertEquals(listOf("Card", "Cash"), serialized.getJSONArray("excludedTotalUsedAccounts").let { array ->
            List(array.length()) { index -> array.getString(index) }
        })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedPaymentTypesInBackups() {
        TrackerData.fromBackup(
            JSONObject(
                """
                {
                  "categories": [],
                  "accounts": [],
                  "transactions": [{
                    "id": "txn-1",
                    "date": "2026-10-17",
                    "description": "Unsupported",
                    "category": "Other",
                    "amount": 1,
                    "account": "Cash",
                    "paymentType": "unknown"
                  }]
                }
                """.trimIndent(),
            ),
        )
    }
}
