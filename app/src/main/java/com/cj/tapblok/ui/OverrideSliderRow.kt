package com.cj.tapblok.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Slider row with a toggle that switches between "use the global value" (null) and "override
 * with this value". Used by Groups (static overrides) and Time Rules (per-window overrides).
 */
@Composable
fun OverrideSliderRow(
    label: String,
    overrideValue: Int?,
    globalValueDisplay: String,
    rangeMax: Int,
    stepUnitLabel: String,
    editable: Boolean,
    rangeMin: Int = 1,
    onChange: (Int?) -> Unit
) {
    val useGlobal = overrideValue == null
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            text = when {
                useGlobal -> "Global ($globalValueDisplay)"
                stepUnitLabel.isEmpty() -> overrideValue.toString()
                overrideValue == 0 -> "Off"
                else -> "$overrideValue $stepUnitLabel"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = !useGlobal,
            enabled = editable,
            onCheckedChange = { override ->
                if (override) onChange(rangeMin.coerceAtLeast(1)) else onChange(null)
            }
        )
    }
    if (!useGlobal) {
        Slider(
            value = overrideValue!!.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = rangeMin.toFloat()..rangeMax.toFloat(),
            steps = (rangeMax - rangeMin - 1).coerceAtLeast(0),
            enabled = editable
        )
    }
}
