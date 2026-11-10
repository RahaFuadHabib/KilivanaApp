package com.example.farmapp

/** Requires a genuine backend-authenticated Buyer session retained in the target app. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiresAuthenticatedBuyer
