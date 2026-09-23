package cn.traintrip.app

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

enum class ThemeChoice(val id: String, val label: String) {
    BLUE("blue", "晴空蓝"),
    GREEN("green", "松林绿"),
    ORANGE("orange", "暖阳橙"),
    PURPLE("purple", "暮山紫");

    companion object {
        fun fromId(id: String?): ThemeChoice = entries.firstOrNull { it.id == id } ?: BLUE
    }
}

interface ThemePreference {
    fun read(): ThemeChoice
    fun save(choice: ThemeChoice)
}

/** A failed write must not change the last durable choice or any other app preferences. */
class AndroidThemePreference(private val file: AtomicFile) : ThemePreference {
    constructor(context: Context) : this(AtomicFile(File(context.applicationContext.filesDir, "theme-choice")))

    @Synchronized override fun read(): ThemeChoice = try {
        file.openRead().bufferedReader(Charsets.UTF_8).use { ThemeChoice.fromId(it.readText().trim()) }
    } catch (e: FileNotFoundException) {
        if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw e
        ThemeChoice.BLUE
    }

    @Synchronized override fun save(choice: ThemeChoice) {
        val output = file.startWrite()
        try {
            output.write(choice.id.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        } catch (e: Exception) {
            file.failWrite(output)
            throw e
        }
        file.finishWrite(output)
        // AtomicFile logs rename errors instead of throwing; verify the actual bytes,
        // without the default used by read() for a missing or unrecognized preference.
        val saved = file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        if (saved != choice.id) throw IOException("Theme preference was not persisted")
    }
}
