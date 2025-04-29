package com.neko.unusedfilescanner

import android.app.AlertDialog
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var btnPickFolder: Button
    private lateinit var btnScan: Button
    private lateinit var btnExport: Button
    private lateinit var btnSummary: Button
    private lateinit var spinnerFilter: Spinner
    private lateinit var progressBar: ProgressBar
    private lateinit var currentFileText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var checkBoxDataBinding: CheckBox

    private var selectedFolder: DocumentFile? = null
    private lateinit var adapter: ResultAdapter
    private var scanResults: List<ScanResult> = emptyList()

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val pickedDir = DocumentFile.fromTreeUri(this, uri)
            if (pickedDir != null && pickedDir.isDirectory) {
                selectedFolder = pickedDir
                Toast.makeText(this, "Folder selected: ${pickedDir.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to access folder", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnPickFolder = findViewById(R.id.btnPickFolder)
        btnScan = findViewById(R.id.btnScan)
        btnExport = findViewById(R.id.btnExport)
        btnSummary = findViewById(R.id.btnSummary)
        spinnerFilter = findViewById(R.id.spinnerFilter)
        progressBar = findViewById(R.id.progressBar)
        currentFileText = findViewById(R.id.currentFileText)
        recyclerView = findViewById(R.id.recyclerView)
        checkBoxDataBinding = findViewById(R.id.checkBoxDataBinding)

        adapter = ResultAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        setupSpinner()

        btnPickFolder.setOnClickListener { checkAndPickFolder() }
        btnScan.setOnClickListener { scanProject() }
        btnExport.setOnClickListener { exportResult() }
        btnSummary.setOnClickListener { showSummaryDialog() }

        btnSummary.visibility = View.GONE
        progressBar.visibility = View.GONE
        currentFileText.visibility = View.GONE
    }

    private fun setupSpinner() {
        val options = listOf(
            "All", "Drawable", "Layout", "Font", "Raw", "Anim", "Xml",
            "Color", "String", "Dimen", "Bool", "Integer", "String Array",
            "Attr", "Declare-Styleable", "Style", "Other Resource", "Unused Dependency"
        )
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFilter.adapter = spinnerAdapter
        spinnerFilter.setSelection(0)

        spinnerFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                filterResults()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun checkAndPickFolder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openFolderPicker()
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    200
                )
            } else {
                openFolderPicker()
            }
        }
    }

    private fun openFolderPicker() {
        folderPickerLauncher.launch(null)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                openFolderPicker()
            } else {
                Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun scanProject() {
        val folder = selectedFolder
        if (folder == null || !folder.isDirectory) {
            Toast.makeText(this, "No folder selected.", Toast.LENGTH_SHORT).show()
            return
        }

        val enableDataBindingScan = checkBoxDataBinding.isChecked

        progressBar.progress = 0
        progressBar.visibility = View.VISIBLE
        currentFileText.text = ""
        currentFileText.visibility = View.VISIBLE
        adapter.setResults(emptyList())
        btnExport.isEnabled = false
        btnSummary.visibility = View.GONE

        coroutineScope.launch {
            try {
                scanResults = withContext(Dispatchers.IO) {
                    ProjectScanner.scanUnusedFromDocumentFile(
                        context = this@MainActivity,
                        projectDir = folder,
                        enableDataBindingScan = enableDataBindingScan
                    ) { progress, fileName ->
                        runOnUiThread {
                            progressBar.progress = progress
                            currentFileText.text = fileName
                        }
                    }
                }

                filterResults()

                if (scanResults.isEmpty()) {
                    Toast.makeText(this@MainActivity, "No unused resources found.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Scan complete!", Toast.LENGTH_SHORT).show()
                }

                btnExport.isEnabled = scanResults.isNotEmpty()
                btnSummary.visibility = if (scanResults.isNotEmpty()) View.VISIBLE else View.GONE

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "Error: ${e.localizedMessage ?: e.toString()}", Toast.LENGTH_LONG).show()
            } finally {
                progressBar.visibility = View.GONE
                currentFileText.visibility = View.GONE
            }
        }
    }

    private fun filterResults() {
        val filter = spinnerFilter.selectedItem?.toString() ?: "All"
        val filtered = when (filter) {
            "All" -> scanResults
            "Unused Dependency" -> scanResults.filter { it.type == "Unused Dependency" }
            else -> scanResults.filter { it.fileName.startsWith(filter.lowercase() + "/") }.ifEmpty { scanResults }
        }
        adapter.setResults(filtered)
    }

    private fun exportResult() {
        if (scanResults.isNotEmpty()) {
            val folder = selectedFolder
            if (folder != null && folder.isDirectory) {
                val fileName = "unused_scan_result_${System.currentTimeMillis()}.csv"
                val csvFile = folder.createFile("text/csv", fileName)

                if (csvFile != null) {
                    try {
                        contentResolver.openOutputStream(csvFile.uri)?.use { outputStream ->
                            outputStream.writer().use { writer ->
                                writer.append("Type,File,Detail\n")
                                scanResults.forEach {
                                    writer.append("${it.type},${it.fileName},${it.detail}\n")
                                }
                            }
                        }
                        Toast.makeText(this, "Exported to: ${csvFile.name}", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "Failed to create file", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "No folder selected.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSummaryDialog() {
        val summary = ProjectScanner.getSummary(scanResults)
        val message = """
            Drawables: ${summary.unusedDrawables}
            Layouts: ${summary.unusedLayouts}
            Colors: ${summary.unusedColors}
            Strings: ${summary.unusedStrings}
            Dimens: ${summary.unusedDimens}
            Bools: ${summary.unusedBool}
            Integers: ${summary.unusedInteger}
            String Arrays: ${summary.unusedStringArray}
            Attrs: ${summary.unusedAttr}
            Declare-Styleables: ${summary.unusedDeclareStyleable}
            Styles: ${summary.unusedStyle}
            Dependencies: ${summary.unusedDependencies}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Scan Summary")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
