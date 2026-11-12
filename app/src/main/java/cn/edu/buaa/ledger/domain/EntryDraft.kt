package cn.edu.buaa.ledger.domain

import java.io.Serializable
import java.util.UUID

/** Editable proposal, never a persisted transaction. Empty fields require human input. */
data class EntryDraft(
    val id: String = UUID.randomUUID().toString(),
    val amount: String = "",
    val date: String = "",
    val type: String = "expense",
    val category: String = "other",
    val note: String = "",
    val source: String = "manual",
    val sourceText: String = "",
    val prompts: List<String> = emptyList()
) : Serializable
