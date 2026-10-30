package com.example.farmapp.buyers

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.farmapp.buyers.data.EmailVerificationChallenge
import com.example.farmapp.buyers.ui.screens.AuthScreen
import com.example.farmapp.buyers.ui.theme.KilivanabuyerTheme
import com.example.farmapp.data.model.SignupRequest
import com.example.farmapp.network.canonical.EmailVerificationPending
import com.example.farmapp.ui.screens.auth.EmailVerificationApi
import com.example.farmapp.ui.screens.auth.EmailVerificationModel
import com.example.farmapp.viewmodel.AuthViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** Original Brian forms, with no second transport, token store, or registration flow. */
@Composable internal fun BrianAuthentication(vm:AuthViewModel,signup:Boolean,switch:()->Unit,back:()->Unit,recover:()->Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    KilivanabuyerTheme {
        AuthScreen(signup,submit={ form ->
            if(!vm.uiState.value.isInitializing && !vm.uiState.value.isLoading) {
                if(signup) vm.signup(SignupRequest(form.email,form.password,form.name,form.phone,"BUYER",form.username,form.region))
                else vm.login(form.email,form.password)
                vm.uiState.first{!it.isLoading}
            }
            vm.uiState.value.errorMessage
        },forgotPassword={recover();null},switch=switch,back=back,externalLoading=state.isLoading || state.isInitializing || state.emailVerification!=null,externalError=state.errorMessage)
    }
}

@Composable internal fun BrianVerification(pending:EmailVerificationPending,api:EmailVerificationApi,verified:()->Unit,back:()->Unit) {
    val model:EmailVerificationModel=viewModel(key="email-verification-${pending.flowId}",factory=EmailVerificationModel.factory(api))
    val state by model.state.collectAsStateWithLifecycle()
    var now by remember{mutableLongStateOf(SystemClock.elapsedRealtime())}
    LaunchedEffect(pending.flowId){model.begin(pending)}
    LaunchedEffect(state.verified){if(state.verified)verified()}
    LaunchedEffect(Unit){while(true){now=SystemClock.elapsedRealtime();delay(1000)}}
    KilivanabuyerTheme {
        com.example.farmapp.buyers.ui.screens.EmailVerificationScreen(
            pending.email,EmailVerificationChallenge(state.challenge?.challengeId?.toString().orEmpty(),state.challenge?.expiresInSeconds ?: 0,0),
            requestCode={
                model.request();val current=model.state.first{!it.busy}
                check(current.error==null){current.error.orEmpty()}
                val challenge=checkNotNull(current.challenge){"Request a verification code when available."}
                EmailVerificationChallenge(challenge.challengeId.toString(),challenge.expiresInSeconds ?: 0,challenge.resendAfterSeconds ?: 60)
            },verifyCode={_,_,code ->
                model.verify(code);val current=model.state.first{!it.busy}
                check(current.verified){current.error ?: "Verification was not confirmed."}
            },continueToSignIn=back,back=back,externalLoading=state.busy,
            canVerify=state.challenge!=null && now>=state.retryAt,
            canResend=now>=state.resendAt && now>=state.retryAt)
    }
}

