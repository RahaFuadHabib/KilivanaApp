// Integrated from brianmuigai2-stack/Kilivana-buyer master 8f335e2; see docs/BRIAN_BUYER_INTEGRATION.md.
package com.example.farmapp.buyers.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.farmapp.buyers.data.PaymentStatus
import com.example.farmapp.buyers.model.*
import com.example.farmapp.buyers.ui.theme.*

@Composable fun OrderConfirmationScreen(id:String,track:()->Unit){Column(Modifier.fillMaxSize().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Icon(Icons.Default.CheckCircle,"Order placed",tint=Green600,modifier=Modifier.size(64.dp));Text("Order placed",style=MaterialTheme.typography.headlineMedium);Text("Order number $id");Button(track){Text("Track order")}}}

@Composable
fun PaymentStatusScreen(payment: PaymentStatus?, loading:Boolean, error:String?, refresh:()->Unit, continueShopping:()->Unit) {
    Column(
        Modifier.fillMaxSize().background(NeutralBackground).verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.Center
    ) {
        Icon(Icons.Default.PhoneAndroid,null,tint=Green700,modifier=Modifier.size(52.dp))
        Spacer(Modifier.height(16.dp))
        Text("M-PESA payment",style=MaterialTheme.typography.headlineMedium,color=TextPrimary)
        Spacer(Modifier.height(20.dp))
        if(loading) CircularProgressIndicator()
        error?.let{Text(it,color=ErrorRed)}
        payment?.let {
            val outcome=com.example.farmapp.orders.buyerPaymentOutcome(it.status)
            Text(when(outcome){"confirmed"->"Payment confirmed";"pending"->"Payment pending";"failed"->"Payment failed";"refunded"->"Payment refunded";else->"Payment not confirmed"},style=MaterialTheme.typography.titleLarge,color=if(outcome=="confirmed")Green700 else TextPrimary)
            Text("Amount: ${formatKes(it.amount)}",color=TextSecondary)
            if(outcome=="pending")Text("Complete the prompt on your phone. Payment status updates after M-PESA responds.",color=TextSecondary)
            if(outcome=="unverified")Text("Keep checking status before attempting another payment.",color=TextSecondary)
        }
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick=refresh,enabled=!loading){Text("Check payment status")}
        Button(onClick=continueShopping){Text("View order")}
    }
}
