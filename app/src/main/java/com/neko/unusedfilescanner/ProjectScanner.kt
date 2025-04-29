package com.neko.unusedfilescanner

import android.content.Context
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.Locale

object ProjectScanner {

    private val defaultIgnoreResourceNames = listOf(
        "ic_launcher", "ic_launcher_round",
        "mipmap-anydpi", "mipmap-xxxhdpi", "mipmap-xxhdpi", "mipmap-xhdpi", "mipmap-hdpi", "mipmap-mdpi",
        "colorPrimary", "colorPrimaryDark", "colorAccent", "colorSurface", "colorOnSurface",
        "colorBackground", "colorOnBackground", "colorSecondary", "colorOnSecondary",
        "colorError", "colorOnError",
        "navigation_", "nav_", "action_", "themeOverlay", "theme_", "material_", "styleable_"
    )

    suspend fun scanUnusedFromDocumentFile(
        context: Context,
        projectDir: DocumentFile,
        enableDataBindingScan: Boolean = true,
        onProgressUpdate: ((Int, String) -> Unit)? = null
    ): List<ScanResult> {
        val results = mutableListOf<ScanResult>()
        val ignoreResourceNames = loadIgnoreList("ignore_resources.txt", defaultIgnoreResourceNames)

        val srcFolders = listOf(
            findSubFolder(projectDir, "src/main/java"),
            findSubFolder(projectDir, "src/main/kotlin")
        ).filterNotNull()

        val resFolder = findSubFolder(projectDir, "src/main/res")
        val manifestFile = findSubFile(projectDir, "src/main/AndroidManifest.xml")

        val allCodeFiles = mutableListOf<DocumentFile>()
        srcFolders.forEach { folder -> allCodeFiles.addAll(collectCodeFiles(folder)) }
        manifestFile?.let { allCodeFiles.add(it) }

        val totalSteps = allCodeFiles.size + 1
        var currentStep = 0

        allCodeFiles.forEach { file ->
            currentStep++
            onProgressUpdate?.invoke((currentStep * 100) / totalSteps, file.name ?: "")
        }

        val allContentBuilder = StringBuilder()
        allCodeFiles.forEach { file -> allContentBuilder.appendLine(readDocumentFileText(context, file)) }
        resFolder?.listFiles()?.forEach { folder ->
            if (folder.isDirectory) {
                folder.listFiles()?.forEach { file ->
                    if (file.isFile && file.name?.endsWith(".xml") == true) {
                        allContentBuilder.appendLine(readDocumentFileText(context, file))
                    }
                }
            }
        }
        val allContent = allContentBuilder.toString()
        val usedLayoutsFromBinding = if (enableDataBindingScan) extractUsedLayoutsFromBinding(allContent) else emptySet()

        // Scan unused files in res/
        resFolder?.listFiles()?.forEach { folder ->
            if (folder.isDirectory) {
                folder.listFiles()?.forEach { resFile ->
                    if (resFile.isFile) {
                        val folderName = folder.name?.substringBefore('-') ?: ""
                        val fileName = resFile.name?.substringBeforeLast('.') ?: ""
                        if (!folderName.startsWith("values")) {
                            if (ignoreResourceNames.any { fileName.contains(it) }) return@forEach
                            val resourcePatterns = listOf(
                                "R.$folderName.$fileName",
                                "@$folderName/$fileName",
                                "\"@$folderName/$fileName\""
                            )
                            val stylePattern = if (folderName == "style") "R.$folderName.${fileName.replace('.', '_')}" else null
                            val mipmapPattern = if (folderName == "mipmap") "R.mipmap.$fileName" else null
                            val fontPattern = if (folderName == "font") "R.font.$fileName" else null
                            val isLayoutUsedFromBinding = folderName == "layout" && usedLayoutsFromBinding.contains(fileName)

                            val matched = resourcePatterns.any { allContent.contains(it) } ||
                                (stylePattern != null && allContent.contains(stylePattern)) ||
                                (mipmapPattern != null && allContent.contains(mipmapPattern)) ||
                                (fontPattern != null && allContent.contains(fontPattern)) ||
                                isLayoutUsedFromBinding

                            if (!matched) {
                                results.add(
                                    ScanResult("Unused Resource", "${folder.name}/${resFile.name}", "Not referenced")
                                )
                            }
                        }
                    }
                }
            }
        }

        onProgressUpdate?.invoke(100, "")

        val valuesFolder = resFolder?.findFile("values")
        if (valuesFolder != null && valuesFolder.isDirectory) {
            val declaredAttrsInStyleable = mutableSetOf<String>()
            valuesFolder.listFiles()?.forEach { file ->
                if (file.isFile && file.name?.endsWith(".xml") == true) {
                    declaredAttrsInStyleable.addAll(extractAttrsInsideDeclareStyleable(readDocumentFileText(context, file)))
                }
            }

            valuesFolder.listFiles()?.forEach { file ->
                if (file.isFile && file.name?.endsWith(".xml") == true) {
                    val xmlContent = readDocumentFileText(context, file)

                    listOf("string", "color", "dimen", "bool", "integer", "string-array", "integer-array", "style", "attr", "declare-styleable").forEach { tag ->
                        val names = extractResourceNames(xmlContent, tag)
                        names.forEach { name ->
                            if (ignoreResourceNames.any { name.contains(it) }) return@forEach
                            if (tag == "attr" && declaredAttrsInStyleable.contains(name)) return@forEach

                            val basePatterns = listOf(
                                "@$tag/$name", "\"@$tag/$name\"",
                                "@$tag:$name", "\"@$tag:$name\"",
                                "@+id/$name", "@id/$name",
                                "R.$tag.$name", "?attr/$name"
                            )
                            val extraPatterns = mutableListOf<String>()
                            if (tag == "string-array" || tag == "integer-array") {
                                extraPatterns.add("@array/$name")
                                extraPatterns.add("R.array.$name")
                            }
                            if (tag == "style") {
                                extraPatterns.add("R.$tag.${name.replace('.', '_')}")
                            }
                            if (tag == "declare-styleable") {
                                extraPatterns.add("R.styleable.$name")
                            }

                            val allPatterns = basePatterns + extraPatterns
                            val matched = allPatterns.any { allContent.contains(it) }

                            if (!matched) {
                                results.add(ScanResult("Unused Resource", "$tag/$name", "Not referenced"))
                            }
                        }
                    }
                }
            }
        }

        // SCAN UNUSED DEPENDENCIES
        val gradleFile = findSubFile(projectDir, "build.gradle")
            ?: findSubFile(projectDir, "build.gradle.kts")

        if (gradleFile != null && gradleFile.isFile) {
            val gradleText = readDocumentFileText(context, gradleFile)
            val dependencyRegex = Regex("""implementation\s*\(?["']([\w\.\-]+):([\w\.\-]+):[\w\.\-]+["']\)?""")
            val ignoreLines = gradleText.lines().filter { it.contains("implementation(") && it.contains("libs.") }
            val dependencies = dependencyRegex.findAll(gradleText)
                .map { it.groupValues[1] to it.groupValues[2] }
                .filterNot { (group, _) -> group.startsWith("libs") }
                .toList()
            val sourceText = allCodeFiles.joinToString("\n") { readDocumentFileText(context, it) }

            dependencies.forEach { (group, artifact) ->
                val groupPath = group.replace("-", ".")
                val artifactPath = artifact.replace("-", ".")
                val used = sourceText.contains(groupPath) || sourceText.contains(artifactPath)
                if (!used) {
                    results.add(ScanResult("Unused Dependency", gradleFile.name ?: "build.gradle", "$group:$artifact"))
                }
            }
        }

        return results
    }

    private fun collectCodeFiles(folder: DocumentFile): List<DocumentFile> {
        return folder.listFiles().flatMap { file ->
            when {
                file.isDirectory -> collectCodeFiles(file)
                file.isFile && (file.name?.endsWith(".kt") == true || file.name?.endsWith(".java") == true) -> listOf(file)
                else -> emptyList()
            }
        }
    }

    private fun findSubFolder(root: DocumentFile, path: String): DocumentFile? {
        var current = root
        path.split("/").forEach { name ->
            current = current.findFile(name) ?: return null
        }
        return current
    }

    private fun findSubFile(root: DocumentFile, path: String): DocumentFile? {
        var current = root
        val parts = path.split("/")
        for (i in 0 until parts.size - 1) {
            current = current.findFile(parts[i]) ?: return null
        }
        return current.findFile(parts.last())
    }

    private fun readDocumentFileText(context: Context, file: DocumentFile): String {
        return context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() } ?: ""
    }

    private fun extractUsedLayoutsFromBinding(content: String): Set<String> {
        val regex = Regex("""(\w+)Binding""")
        return regex.findAll(content).mapNotNull { it.groupValues[1] }.map { camelToSnake(it) }.toSet()
    }

    private fun camelToSnake(name: String): String {
        return name.replace(Regex("([a-z])([A-Z]+)"), "$1_$2")
            .replace(Regex("([A-Z])([A-Z][a-z])"), "$1_$2")
            .lowercase(Locale.ROOT)
    }

    private fun extractResourceNames(xmlContent: String, tag: String): List<String> {
        val regex = Regex("<$tag name=\"(.*?)\"")
        return regex.findAll(xmlContent).map { it.groupValues[1] }.toList()
    }

    private fun extractAttrsInsideDeclareStyleable(xmlContent: String): List<String> {
        val results = mutableListOf<String>()
        var inside = false
        xmlContent.lineSequence().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("<declare-styleable") -> inside = true
                trimmed.startsWith("</declare-styleable") -> inside = false
                inside && trimmed.startsWith("<attr") -> {
                    Regex("name\\s*=\\s*\"(.*?)\"").find(trimmed)?.groupValues?.get(1)?.let { results.add(it) }
                }
            }
        }
        return results
    }

    private fun loadIgnoreList(fileName: String, defaultList: List<String>): List<String> {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val file = File(dir, "UnusedFileScanner/$fileName")
        return if (file.exists() && file.isFile) file.readLines().map { it.trim() }.filter { it.isNotEmpty() } else defaultList
    }

    fun getSummary(results: List<ScanResult>): ScanSummary {
        return ScanSummary(
            unusedDrawables = results.count { it.type == "Unused Resource" && it.fileName.startsWith("drawable/") },
            unusedLayouts = results.count { it.type == "Unused Resource" && it.fileName.startsWith("layout/") },
            unusedColors = results.count { it.type == "Unused Resource" && it.fileName.startsWith("color/") },
            unusedStrings = results.count { it.type == "Unused Resource" && it.fileName.startsWith("string/") },
            unusedDimens = results.count { it.type == "Unused Resource" && it.fileName.startsWith("dimen/") },
            unusedBool = results.count { it.type == "Unused Resource" && it.fileName.startsWith("bool/") },
            unusedInteger = results.count { it.type == "Unused Resource" && it.fileName.startsWith("integer/") },
            unusedStringArray = results.count { it.type == "Unused Resource" && it.fileName.startsWith("string-array/") },
            unusedAttr = results.count { it.type == "Unused Resource" && it.fileName.startsWith("attr/") },
            unusedDeclareStyleable = results.count { it.type == "Unused Resource" && it.fileName.startsWith("declare-styleable/") },
            unusedStyle = results.count { it.type == "Unused Resource" && it.fileName.startsWith("style/") },
            unusedDependencies = results.count { it.type == "Unused Dependency" }
        )
    }
}
