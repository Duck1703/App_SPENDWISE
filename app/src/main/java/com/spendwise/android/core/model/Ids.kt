package com.spendwise.android.core.model

// Typed ID value classes (AD-007): String-based, backend-issued, never
// manufactured on the client. Feature usage arrives with M1+ features;
// these establish the pattern and prevent raw-String drift from the start.

@JvmInline
value class AccountId(val value: String)

@JvmInline
value class TransactionId(val value: String)

@JvmInline
value class RecurringId(val value: String)

@JvmInline
value class BudgetId(val value: String)

@JvmInline
value class RuleId(val value: String)

@JvmInline
value class ImportBatchId(val value: String)
