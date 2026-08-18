package com.futurpayment.softpos

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); window.setFlags(WindowManager.LayoutParams.FLAG_SECURE,WindowManager.LayoutParams.FLAG_SECURE)
        setContent { MaterialTheme { SoftPosHome() } }
    }
}

@Composable private fun SoftPosHome() {
    var amount by remember { mutableStateOf("") }; var channel by remember { mutableStateOf("NFC") }
    Surface(Modifier.fillMaxSize()) { Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("FuturPayment SoftPOS",style=MaterialTheme.typography.headlineMedium); Text("Terminal mobile sécurisé")
        OutlinedTextField(amount,{amount=it.filter(Char::isDigit)},label={Text("Montant en centimes")})
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("NFC","QR_MPM","QR_CPM").forEach{FilterChip(selected=channel==it,onClick={channel=it},label={Text(it)})}}
        Button(onClick={/* PaymentCoordinator is injected by the production application graph. */},enabled=amount.isNotBlank()){Text("Encaisser")}
        Text("Aucune donnée carte ou clé n’est stockée par l’application.",style=MaterialTheme.typography.bodySmall)
    }}
}
