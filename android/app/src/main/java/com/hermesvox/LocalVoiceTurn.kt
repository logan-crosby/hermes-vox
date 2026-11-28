package com.hermesvox

/** Keeps typed input behind the voice turn that owns the phone action. */
internal class LocalVoiceTurn {
    data class Typed(val text: String, val pasted: Boolean, val display: String? = null)

    private var owner: Long? = null
    private val typed = ArrayDeque<Typed>()

    @Synchronized fun begin(gen: Long) {
        owner = gen
    }

    @Synchronized fun active(gen: Long): Boolean = owner == gen

    @Synchronized fun deferTyped(gen: Long, text: String, pasted: Boolean, display: String? = null): Boolean {
        if (owner != gen && typed.isEmpty()) return false
        typed.addLast(Typed(text, pasted, display))
        return true
    }

    @Synchronized fun finish(gen: Long) {
        if (owner == gen) owner = null
    }

    @Synchronized fun hasTyped(): Boolean = typed.isNotEmpty()

    @Synchronized fun takeTyped(): Typed? = if (owner == null) typed.removeFirstOrNull() else null

    @Synchronized fun clear() {
        owner = null
        typed.clear()
    }
}
