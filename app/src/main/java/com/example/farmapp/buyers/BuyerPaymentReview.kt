package com.example.farmapp.buyers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.farmapp.buyers.ui.screens.CheckoutSection
import com.example.farmapp.buyers.ui.screens.SummaryLine
import com.example.farmapp.buyers.ui.theme.*
import com.example.farmapp.buyers.model.formatKes
import com.example.farmapp.orders.*
import java.math.BigDecimal

/** Brian checkout/payment section components with the existing verified-order STK controller. */
@Composable internal fun BrianPaymentReview(order:BuyerOrderDto,state:BuyerPaymentState,back:()->Unit,refresh:()->Unit,send:(String,BigDecimal?)->Unit,initialPhone:String="") {
    if(state.payments.isNotEmpty() || state.submitted) {
        val payment=state.payments.lastOrNull()
        com.example.farmapp.buyers.ui.screens.PaymentStatusScreen(
            com.example.farmapp.buyers.data.PaymentStatus(payment?.status ?: "UNKNOWN",payment?.amount ?: state.total ?: order.total,null),state.busy,state.notice,refresh,back)
        return
    }
    var phone by rememberSaveable(order.id){mutableStateOf(initialPhone)}
    var confirm by remember(order.id){mutableStateOf(false)}
    var reviewed by remember(order.id){mutableStateOf<BigDecimal?>(null)}
    Column(Modifier.fillMaxSize().background(NeutralBackground).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        TextButton(onClick=back){Text("Back to order")}
        Text("M-Pesa payment",style=MaterialTheme.typography.headlineMedium,color=TextPrimary)
        CheckoutSection("Order summary") {
            SummaryLine("Items subtotal",formatKes(order.subtotal));SummaryLine("Delivery",formatKes(order.deliveryFee))
            SummaryLine("Total",formatKes(state.total ?: order.total),true)
        }
        CheckoutSection("Phone number") {
            Text("Confirm to receive an M-Pesa prompt on your phone. Enter your PIN only in the M-Pesa prompt.",color=TextSecondary)
            OutlinedTextField(phone,{phone=it},Modifier.fillMaxWidth(),label={Text("Kenyan mobile number")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone),singleLine=true,enabled=!state.busy)
            if(phone.isNotBlank() && runCatching{buyerMpesaPhone(phone)}.isFailure) Text("Enter a valid Kenyan M-Pesa number.",color=ErrorRed)
        }
        if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        state.notice?.let{Text(it,color=TextSecondary)}
        state.payments.forEach{Text("Payment: ${buyerPaymentOutcome(it.status)} - ${formatKes(it.amount)}")}
        Button(onClick={reviewed=state.total;confirm=true},enabled=state.canInitiate && state.submissionEnabled && !state.busy && runCatching{buyerMpesaPhone(phone)}.isSuccess,modifier=Modifier.fillMaxWidth()){Text("Send M-Pesa prompt")}
        OutlinedButton(onClick=refresh,enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text("Check payment status")}
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("Send M-Pesa prompt?")},text={Text("${formatKes(reviewed)} to ${runCatching{buyerMpesaPhone(phone)}.getOrDefault("")}. Confirm only if you want to initiate payment.")},
        confirmButton={TextButton(onClick={confirm=false;send(phone,reviewed)}){Text("Confirm STK Push")}},dismissButton={TextButton(onClick={confirm=false}){Text("Cancel")}})
}

