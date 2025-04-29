package com.neko.unusedfilescanner

data class ScanResult(
    val type: String,
    val fileName: String,
    val detail: String
)

data class ScanSummary(
    val unusedDrawables: Int,
    val unusedLayouts: Int,
    val unusedColors: Int,
    val unusedStrings: Int,
    val unusedDimens: Int,
    val unusedBool: Int,
    val unusedInteger: Int,
    val unusedStringArray: Int,
    val unusedAttr: Int,
    val unusedDeclareStyleable: Int,
    val unusedStyle: Int,
    val unusedDependencies: Int = 0
)
