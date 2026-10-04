package com.mar.demo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mar.agent.sdk.db.WorkflowRepository
import com.mar.demo.ui.fragments.YamlEditorFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class YamlShareActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent?.action

        if (Intent.ACTION_VIEW == action) {
            handleSharedYaml(intent?.data, intent?.type)
        } else {
            showEditor()
        }
    }

    private fun showEditor() {
        val agentId = intent?.getStringExtra("agent_id")
        if (agentId != null) {
            CoroutineScope(Dispatchers.IO).launch {
                val repo = WorkflowRepository(this@YamlShareActivity)
                val wf = repo.getById(agentId)
                val yaml = wf?.yaml ?: ""
                runOnUiThread {
                    openEditorWithYaml(agentId, yaml)
                }
            }
        } else {
            openEditorWithYaml(null, null)
        }
    }

    private fun openEditorWithYaml(agentId: String?, yamlContent: String?) {
        val fragment = YamlEditorFragment()
        val args = Bundle()
        if (agentId != null) {
            args.putString("AGENT_ID", agentId)
        }
        if (yamlContent != null) {
            args.putString("DEFAULT_YAML", yamlContent)
        }
        fragment.arguments = args

        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .commit()
    }

    private fun handleSharedYaml(uri: Uri?, mimeType: String?) {
        if (uri == null) {
            Toast.makeText(this, "No valid agent mapping found.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        try {
            val yamlParam = uri.getQueryParameter("yaml")
            if (yamlParam != null) {
                // Decode from mar-agent://install?yaml=...
                val bytes = android.util.Base64.decode(yamlParam, android.util.Base64.URL_SAFE)
                val input = java.io.ByteArrayInputStream(bytes)
                val decompressedBytes = java.util.zip.GZIPInputStream(input).readBytes()
                val yamlText = String(decompressedBytes)
                
                // Validate YAML
                val validation = com.mar.agent.sdk.yaml.parser.YamlValidator.validate(yamlText)
                if (!validation.isValid) {
                    val errors = validation.errors.joinToString("\n")
                    runOnUiThread {
                        android.app.AlertDialog.Builder(this@YamlShareActivity)
                            .setTitle("Invalid Agent YAML")
                            .setMessage("Cannot import this agent because it contains errors:\n\n$errors")
                            .setPositiveButton("OK") { _, _ -> finish() }
                            .show()
                    }
                    return
                }

                // Parse it to get the name
                val config = com.mar.agent.sdk.yaml.parser.MarYamlParser().parse(yamlText.byteInputStream())
                val agentName = config.agent.name.ifBlank { "Shared Agent" }
                val agentDesc = config.agent.description.ifBlank { "Imported via QR/Link" }
                
                CoroutineScope(Dispatchers.IO).launch {
                    val repo = WorkflowRepository(this@YamlShareActivity)
                    repo.save(java.util.UUID.randomUUID().toString(), agentName, agentDesc, yamlText)
                    runOnUiThread {
                        Toast.makeText(this@YamlShareActivity, "Imported '$agentName' successfully!", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
                return
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to import agent: ${e.message}", Toast.LENGTH_LONG).show()
        }

        finish()
    }
}
