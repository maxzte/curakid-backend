package com.misterio.curakid.core.essential.common.events

import com.misterio.curakid.core.essential.domain.ChildId
import java.util.UUID

data class ChildCreatedEvent(
    val childId: UUID
) {
    val typedChildId: ChildId
        get() = ChildId(childId)

    companion object {
        fun from(id: ChildId): ChildCreatedEvent = ChildCreatedEvent(id.value)
    }
}
