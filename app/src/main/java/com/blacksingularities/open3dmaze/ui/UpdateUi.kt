package com.blacksingularities.open3dmaze.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.open3dmaze.update.Update
import com.blacksingularities.open3dmaze.update.Updates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Sprawdzanie, pobieranie i instalacja aktualizacji przez redzikowski.com. */
class UpdateState(private val context: Context, private val scope: CoroutineScope) {
    var update by mutableStateOf<Update?>(null)
    var checking by mutableStateOf(false)
    var checked by mutableStateOf(false)
    var progress by mutableFloatStateOf(-2f)
    var error by mutableStateOf<String?>(null)

    suspend fun check() {
        if (checking) return
        checking = true
        update = Updates.check()
        checking = false
        checked = true
    }

    fun checkNow() { scope.launch { check() } }

    fun start() {
        val u = update ?: return
        if (!Updates.canInstall(context)) {
            runCatching { context.startActivity(Updates.permissionIntent(context).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        error = null
        progress = 0f
        scope.launch {
            val f = Updates.download(context, u) { progress = it }
            progress = -2f
            if (f != null) Updates.install(context, f) else error = "Nie udało się pobrać aktualizacji."
        }
    }

    @Composable
    fun Banner() {
        val u = update ?: return
        val accent = accentColor()
        Column(
            Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(22.dp)).background(Palette.Panel)
                .border(1.dp, accent, RoundedCornerShape(22.dp)).clickable { start() }.padding(16.dp),
        ) {
            Text("Dostępna wersja ${u.version}", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(
                when {
                    progress >= 0f -> "Pobieranie… ${(progress * 100).toInt()}%"
                    progress == -1f -> "Pobieranie…"
                    !Updates.canInstall(context) -> "Dotknij, aby zezwolić na instalację i zaktualizować"
                    else -> "Dotknij, aby pobrać i zainstalować"
                },
                color = Palette.TextDim, fontSize = 13.sp,
            )
            if (progress >= 0f) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = accent, trackColor = Palette.PanelHigh)
            }
            error?.let { Text(it, color = Palette.Danger, fontSize = 13.sp) }
        }
    }

}

@Composable
fun rememberUpdateState(): UpdateState {
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    return remember { UpdateState(ctx, scope) }
}
