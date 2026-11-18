package dev.mothlight.plantagotchi

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** Widget settings (on add and via long press): which plant the widget shows. */
class PlantWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, result)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val plants = loadSnapshot(this)?.plants.orEmpty()
        setContent {
            Picker(
                options = listOf<Pair<String?, String>>(null to stringResource(R.string.widget_follow_app)) +
                    plants.map { it.id to it.name },
            ) { plantId ->
                lifecycleScope.launch {
                    setWidgetPlant(this@PlantWidgetConfigActivity, appWidgetId, plantId)
                    setResult(RESULT_OK, result)
                    finish()
                }
            }
        }
    }
}

@Composable
private fun Picker(options: List<Pair<String?, String>>, onPick: (String?) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF2B1D24))
            .safeDrawingPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            stringResource(R.string.widget_config_title),
            color = Color(0xFFFFEB57),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        options.forEach { (id, label) ->
            Text(
                label,
                color = Color(0xFFEDEDED),
                fontSize = 17.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(id) }
                    .padding(vertical = 14.dp),
            )
        }
    }
}
