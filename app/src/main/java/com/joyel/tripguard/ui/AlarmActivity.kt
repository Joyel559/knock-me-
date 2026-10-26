package com.joyel.tripguard.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joyel.tripguard.service.ACTION_DISMISS_ARRIVAL
import com.joyel.tripguard.service.ACTION_DISMISS_FINAL_ARRIVAL
import com.joyel.tripguard.service.ACTION_IGNORE_ROUTE
import com.joyel.tripguard.service.ACTION_REROUTE
import com.joyel.tripguard.service.ACTION_STOP
import com.joyel.tripguard.service.AlarmController
import com.joyel.tripguard.service.AlarmKind
import com.joyel.tripguard.service.TripService
import com.joyel.tripguard.ui.theme.TgRed
import com.joyel.tripguard.ui.theme.TripGuardTheme

class AlarmActivity : ComponentActivity() {

    companion object {
        const val EXTRA_KIND = "kind"
        const val EXTRA_TITLE = "title"
        const val EXTRA_MESSAGE = "message"
    }

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        registerReceiver(
            dismissReceiver,
            IntentFilter("com.joyel.tripguard.ACTION_DISMISS_ALARM"),
            RECEIVER_NOT_EXPORTED
        )

        val kind = AlarmKind.valueOf(intent.getStringExtra(EXTRA_KIND) ?: AlarmKind.ARRIVAL.name)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "ALARM"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""

        setContent {
            TripGuardTheme {
                if (kind == AlarmKind.OFF_ROUTE) {
                    OffRouteAlarmScreen(
                        title = title,
                        message = message,
                        onReroute = { sendAndFinish(ACTION_REROUTE) },
                        onIgnore = { sendAndFinish(ACTION_IGNORE_ROUTE) },
                        onStop = { sendAndFinish(ACTION_STOP) }
                    )
                } else {
                    AlarmScreen(
                        title = title,
                        message = message,
                        onAwake = { dismissArrival(kind) },
                        onStop = { sendAndFinish(ACTION_STOP) }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(dismissReceiver) }
        super.onDestroy()
    }

    private fun dismissArrival(kind: AlarmKind) {
        val action = if (kind == AlarmKind.FINAL_ARRIVAL) ACTION_DISMISS_FINAL_ARRIVAL else ACTION_DISMISS_ARRIVAL
        sendAndFinish(action)
    }

    private fun sendAndFinish(action: String) {
        AlarmController.dismiss(this)
        startService(Intent(this, TripService::class.java).setAction(action))
        finish()
    }
}

@Composable
private fun AlarmScreen(title: String, message: String, onAwake: () -> Unit, onStop: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            color = TgRed,
            textAlign = TextAlign.Center
        )
        Text(
            text = message,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp, bottom = 48.dp)
        )
        Button(
            onClick = onAwake,
            modifier = Modifier.height(72.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TgRed)
        ) {
            Text("I'M AWAKE", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }
        OutlinedButton(
            onClick = onStop,
            modifier = Modifier.padding(top = 16.dp).height(56.dp)
        ) {
            Text("Stop Trip", color = Color.White)
        }
    }
}

@Composable
private fun OffRouteAlarmScreen(
    title: String,
    message: String,
    onReroute: () -> Unit,
    onIgnore: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            color = TgRed,
            textAlign = TextAlign.Center
        )
        Text(
            text = message,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp, bottom = 32.dp)
        )
        Button(
            modifier = Modifier.padding(bottom = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
            onClick = onReroute
        ) {
            Text("Re-route from here", color = Color.White)
        }
        OutlinedButton(modifier = Modifier.padding(bottom = 12.dp), onClick = onIgnore) {
            Text("Ignore for 10 min", color = Color.White)
        }
        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(containerColor = TgRed)
        ) {
            Text("Stop trip", color = Color.White)
        }
    }
}
