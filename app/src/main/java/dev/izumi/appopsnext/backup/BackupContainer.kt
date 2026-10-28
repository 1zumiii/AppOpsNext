package dev.izumi.appopsnext.backup

/** One named block of a backup file, read and written independently of the others. */
data class BackupSection(
    val name: String,
    val version: Int,
    val lines: List<String>,
)

/** Why a file could not be read as a backup. Nothing is imported in any of these cases. */
enum class BackupProblem {
    /** Not a backup from this app, or empty. */
    FOREIGN,

    /** Written by a newer build whose format this one cannot read. */
    NEWER_FORMAT,

    /** Starts like a backup but its structure or a section's content is broken. */
    CORRUPT,

    /** A readable backup that holds nothing this build knows how to import. */
    NOTHING_TO_IMPORT,
}

class BackupFormatException(val problem: BackupProblem) : Exception(problem.name)

/**
 * The container shared by every section:
 *
 * ```
 * APP_OPS_NEXT_BACKUP 1
 * @section templates 1
 * ...section lines...
 * @end
 * ```
 *
 * Sections never look inside one another, so a section added later (or one a
 * newer build writes in a newer version) is passed over by a build that does not
 * know it, and the rest of the file still imports. A body line that would start
 * with `@` is written as `@@`, so section content can never end its section early.
 */
object BackupContainer {
    const val FORMAT_VERSION = 1
    private const val HEADER = "APP_OPS_NEXT_BACKUP"
    private const val SECTION = "@section"
    private const val END = "@end"
    private const val MARK = "@"

    /** Some editors add one when a backup is opened and saved again. */
    private val BYTE_ORDER_MARK = Char(0xFEFF)

    fun encode(sections: List<BackupSection>): String =
        buildString {
            appendLine("$HEADER $FORMAT_VERSION")
            sections.forEach { section ->
                appendLine("$SECTION ${section.name} ${section.version}")
                section.lines.forEach { line ->
                    appendLine(if (line.startsWith(MARK)) MARK + line else line)
                }
                appendLine(END)
            }
        }

    /** @throws BackupFormatException when the file is not a backup this build can read. */
    fun decode(text: String): List<BackupSection> {
        val lines = text.removePrefix(BYTE_ORDER_MARK.toString()).lines().map { it.removeSuffix("\r") }
        val header = lines.firstOrNull()?.trim().orEmpty().split(' ')
        if (header.firstOrNull() != HEADER) throw BackupFormatException(BackupProblem.FOREIGN)
        val version = header.getOrNull(1)?.toIntOrNull()
            ?: throw BackupFormatException(BackupProblem.CORRUPT)
        if (version > FORMAT_VERSION) throw BackupFormatException(BackupProblem.NEWER_FORMAT)

        val sections = mutableListOf<BackupSection>()
        var open: Triple<String, Int, MutableList<String>>? = null
        lines.drop(1).forEach { line ->
            val current = open
            when {
                current == null && line.isBlank() -> Unit
                current == null -> {
                    val fields = line.trim().split(' ').filter(String::isNotEmpty)
                    val name = fields.getOrNull(1)
                    val sectionVersion = fields.getOrNull(2)?.toIntOrNull()
                    if (fields.size != 3 || fields[0] != SECTION || name == null ||
                        sectionVersion == null || sectionVersion < 1
                    ) {
                        throw BackupFormatException(BackupProblem.CORRUPT)
                    }
                    if (sections.any { it.name == name }) {
                        throw BackupFormatException(BackupProblem.CORRUPT)
                    }
                    open = Triple(name, sectionVersion, mutableListOf())
                }
                line == END -> {
                    sections += BackupSection(current.first, current.second, current.third)
                    open = null
                }
                line.startsWith(MARK + MARK) -> current.third += line.removePrefix(MARK)
                line.startsWith(MARK) -> throw BackupFormatException(BackupProblem.CORRUPT)
                else -> current.third += line
            }
        }
        if (open != null) throw BackupFormatException(BackupProblem.CORRUPT)
        return sections
    }
}
