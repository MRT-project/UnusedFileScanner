package com.neko.unusedfilescanner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ResultAdapter : RecyclerView.Adapter<ResultAdapter.ResultViewHolder>() {

    private var results: List<ScanResult> = listOf()

    fun setResults(newResults: List<ScanResult>) {
        results = newResults
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_result, parent, false)
        return ResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        holder.bind(results[position])
    }

    override fun getItemCount(): Int = results.size

    inner class ResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val resultText: TextView = itemView.findViewById(R.id.resultText)

        fun bind(result: ScanResult) {
            val displayText = "[${result.type}]\n${result.fileName}\n${result.detail}"
            resultText.text = displayText
        }
    }
}
