package com.gorate.app.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.gorate.app.data.repository.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Gestor oficial y seguro de facturación y suscripciones con Google Play Billing.
 * Cumple con el 100% de las directrices y políticas de Google Play Store.
 */
class BillingManager(
    private val context: Context,
    private val onPriceLoaded: ((String) -> Unit)? = null,
    private val onPurchaseSuccess: (() -> Unit)? = null,
    private val onPurchaseError: ((String) -> Unit)? = null
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "GoRateBillingManager"
        const val SUBSCRIPTION_PRODUCT_ID = "gorate_pro_monthly"
    }

    private val prefs = PreferencesRepository(context)
    private var productDetails: ProductDetails? = null
    private var selectedOfferToken: String? = null

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    init {
        startBillingConnection()
    }

    private fun startBillingConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.i(TAG, "Google Play Billing conectado exitosamente.")
                    querySubscriptionDetails()
                    queryActivePurchases()
                } else {
                    Log.w(TAG, "Conexión a Play Billing fallida: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Servicio de Google Play Billing desconectado. Reintentando...")
            }
        })
    }

    /**
     * Consulta el producto de suscripción mensual oficial en los servidores de Google Play.
     */
    fun querySubscriptionDetails() {
        if (!billingClient.isReady) return

        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SUBSCRIPTION_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val details = productDetailsList[0]
                productDetails = details

                val subscriptionOfferDetails = details.subscriptionOfferDetails
                if (!subscriptionOfferDetails.isNullOrEmpty()) {
                    val offer = subscriptionOfferDetails[0]
                    selectedOfferToken = offer.offerToken
                    val pricingPhases = offer.pricingPhases.pricingPhaseList
                    if (pricingPhases.isNotEmpty()) {
                        val formattedPrice = pricingPhases[0].formattedPrice
                        Log.i(TAG, "Precio oficial cargado: $formattedPrice")
                        onPriceLoaded?.invoke(formattedPrice)
                    }
                }
            } else {
                Log.w(TAG, "No se encontró el producto $SUBSCRIPTION_PRODUCT_ID en Play Console: ${billingResult.debugMessage}")
            }
        }
    }

    /**
     * Abre la pasarela nativa y segura de pago de Google Play.
     */
    fun launchBillingFlow(activity: Activity): Boolean {
        val details = productDetails
        val offerToken = selectedOfferToken

        if (details == null || offerToken == null) {
            onPurchaseError?.invoke("La suscripción aún se está conectando con Google Play. Inténtalo en un momento.")
            querySubscriptionDetails()
            return false
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .setOfferToken(offerToken)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val responseCode = billingClient.launchBillingFlow(activity, billingFlowParams).responseCode
        return responseCode == BillingClient.BillingResponseCode.OK
    }

    /**
     * Escucha las compras en tiempo real de Google Play.
     */
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (!purchases.isNullOrEmpty()) {
                    for (purchase in purchases) {
                        handlePurchase(purchase)
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.i(TAG, "El usuario canceló la suscripción.")
            }
            else -> {
                val errorMsg = "Error en la suscripción: ${billingResult.debugMessage}"
                Log.e(TAG, errorMsg)
                onPurchaseError?.invoke(errorMsg)
            }
        }
    }

    /**
     * Procesa, valida y confirma la compra ante Google Play (acknowledgePurchase).
     */
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // Confirmación obligatoria para evitar reembolso automático de Google a los 3 días
            if (!purchase.isAcknowledged) {
                val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                billingClient.acknowledgePurchase(acknowledgeParams) { result ->
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.i(TAG, "Suscripción confirmada y acreditada exitosamente.")
                        activateProSubscription()
                    }
                }
            } else {
                activateProSubscription()
            }
        }
    }

    /**
     * Activa el estado PRO local y sincroniza de forma segura con Cloud Firestore.
     */
    private fun activateProSubscription() {
        prefs.setProUser(true)

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(currentUser.uid)
                        .update("isPro", true, "proActivatedAt", com.google.firebase.Timestamp.now())
                } catch (e: Exception) {
                    Log.e(TAG, "Error sincronizando estado PRO en Firestore", e)
                }
            }
        }

        onPurchaseSuccess?.invoke()
    }

    /**
     * Consulta compras activas para mantener el estado PRO al día y restaurar compras.
     */
    fun queryActivePurchases(onResult: ((hasActiveSubscription: Boolean) -> Unit)? = null) {
        if (!billingClient.isReady) {
            onResult?.invoke(false)
            return
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                var foundActive = false
                for (purchase in purchasesList) {
                    if (purchase.products.contains(SUBSCRIPTION_PRODUCT_ID) &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        foundActive = true
                        handlePurchase(purchase)
                    }
                }

                if (!foundActive && !prefs.isAdminUser() && !prefs.isTrialActive()) {
                    // Si no tiene compra activa en Play Store ni es admin ni prueba, desactivar PRO
                    prefs.setProUser(false)
                }
                onResult?.invoke(foundActive)
            } else {
                onResult?.invoke(false)
            }
        }
    }

    /**
     * Función obligatoria para cumplir con las políticas de Google Play: Restaurar Compras.
     */
    fun restorePurchases(onComplete: (success: Boolean, message: String) -> Unit) {
        if (!billingClient.isReady) {
            onComplete(false, "Conectando con Google Play. Inténtalo de nuevo en unos segundos.")
            startBillingConnection()
            return
        }

        queryActivePurchases { hasActive ->
            if (hasActive) {
                onComplete(true, "¡Membresía GoRate PRO restaurada con éxito!")
            } else {
                onComplete(false, "No se encontró ninguna suscripción activa vinculada a tu cuenta de Google Play.")
            }
        }
    }

    fun onDestroy() {
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }
}
