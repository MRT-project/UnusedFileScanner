package com.neko.unusedfilescanner

import android.content.Context
import java.io.File
import java.io.FileWriter

object CSVExporter {

    fun export(context: Context, results: List<ScanResult>): File {
        val file = File(context.cacheDir, "unused_scan_result_${System.currentTimeMillis()}.csv")
        val writer = FileWriter(file)
        writer.append("Type,File,Detail\n")
        results.forEach {
            writer.append("${it.type},${it.fileName},${it.detail}\n")
        }
        writer.flush()
        writer.close()
        return file
    }
}
