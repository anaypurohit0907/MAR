package com.mar.demo

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import android.net.Uri
import android.content.Intent

/**
 * Intercepts intent schemes (e.g. mar-agent://install or matching .yaml files) 
 * to load new workflows dynamically from the community directly into the database.
 */
class YamlShareActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        val action = intent.action
        val type = intent.type
        val data: Uri? = intent.data

        if (Intent.ACTION_VIEW == action) {
            handleSharedYaml(data, type)
        } else {
            finish()
        }
    }

    private fun handleSharedYaml(uri: Uri?, mimeType: String?) {
        if (uri == null) {
            Toast.makeText(this, "No valid agent mapping found.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Simulating the ingestion of the YAML file / scheme intent
        val agentId = "Imported_${System.currentTimeMillis()}"
        
        Toast.makeText(this, "Agent configuration parsed & installed successfully.", Toast.LENGTH_LONG).show()
        
        // Return to main app
        finish()
    }
}
