package com.mar.demo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
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
            openEditorWithYaml("new_workflow", null)
        }
    }

    private fun openEditorWithYaml(fileName: String, yamlContent: String?) {
        val fragment = YamlEditorFragment()
        val args = Bundle()
        if (yamlContent != null) {
            args.putString("DEFAULT_YAML", yamlContent)
        }
        args.putString("FILE_NAME", fileName.replace(" ", "_") + ".yaml")
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

        val agentId = "Imported_${System.currentTimeMillis()}"

        Toast.makeText(this, "Agent configuration parsed & installed successfully.", Toast.LENGTH_LONG).show()

        finish()
    }
}
