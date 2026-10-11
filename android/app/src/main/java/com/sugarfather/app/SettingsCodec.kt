package com.sugarfather.app

/** 设置项的手写序列化（无第三方依赖，便于单测） */
object SettingsCodec {

    fun encodeTargets(t: Targets): String =
        "${t.fastingLo},${t.fastingHi},${t.nonFastingLo},${t.nonFastingHi}"

    fun decodeTargets(s: String?): Targets {
        if (s.isNullOrBlank()) return Targets()
        return try {
            val a = s.split(",").map { it.trim().toDouble() }
            if (a.size == 4) Targets(a[0], a[1], a[2], a[3]) else Targets()
        } catch (e: Exception) {
            Targets()
        }
    }

    private fun sanitize(text: String): String =
        text.replace("|", " ").replace(";", " ").replace("\n", " ").trim()

    fun encodeReminders(list: List<Reminder>): String =
        list.take(2).joinToString(";") { r ->
            "${if (r.enabled) 1 else 0}|${r.hour}|${r.minute}|${sanitize(r.text)}"
        }

    fun decodeReminders(s: String?): List<Reminder> {
        val fallback = listOf(
            Reminder(false, 7, 0, REMINDER_TEXTS[0]),
            Reminder(false, 21, 30, REMINDER_TEXTS[2])
        )
        if (s.isNullOrBlank()) return fallback
        return try {
            val list = s.split(";").filter { it.isNotBlank() }.map { part ->
                val f = part.split("|")
                Reminder(
                    enabled = f.getOrNull(0) == "1",
                    hour = f.getOrNull(1)?.toIntOrNull() ?: 7,
                    minute = f.getOrNull(2)?.toIntOrNull() ?: 0,
                    text = f.getOrNull(3) ?: REMINDER_TEXTS[0]
                )
            }
            if (list.isEmpty()) fallback else list
        } catch (e: Exception) {
            fallback
        }
    }
}
