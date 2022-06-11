/**
 * *******************************************************************************
 * Copyright 2016 Tom Praschan
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ******************************************************************************
 */
package de.tap.easy_xkcd.Activities

import android.content.DialogInterface
import android.os.Bundle
import android.os.PersistableBundle
import android.view.Menu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.*
import com.tap.xkcd_reader.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@AndroidEntryPoint
class DonateActivity : BaseActivity() {
    companion object {
        val iap = listOf(
            "de.tap.easy_xkcd.iap1",
            "de.tap.easy_xkcd.iap2",
            "de.tap.easy_xkcd.iap3",
            "de.tap.easy_xkcd.iap4",
            "de.tap.easy_xkcd.iap5",
        )
    }

    private fun finishWithErrorToast() {
        finish()
        Toast.makeText(this@DonateActivity, R.string.iap_failed, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val billingClient = BillingClient.newBuilder(this)
            .setListener { billingResult, purchases ->
                Timber.d("code ${billingResult.responseCode} purchases $purchases")
                if (   billingResult.responseCode == BillingClient.BillingResponseCode.OK
                    && purchases != null) {
                        if (purchases.firstOrNull()?.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            settings.hideDonate = true
                            finish()
                            Toast.makeText(this@DonateActivity, R.string.iap_thanks, Toast.LENGTH_SHORT).show()
                        }
                } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                    Timber.i("Billing was canceled")
                    finish()
                } else {
                    finishWithErrorToast()
                }
            }.enablePendingPurchases()
            .build()

        billingClient.startConnection(object: BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                Timber.e("Lost connection to billing service!")
                Toast.makeText(this@DonateActivity, R.string.iap_failed, Toast.LENGTH_SHORT).show()
                finish()
            }

            override fun onBillingSetupFinished(p0: BillingResult) {
                lifecycleScope.launch {
                    val details = withContext(Dispatchers.IO) {
                        billingClient.querySkuDetails(
                            SkuDetailsParams.newBuilder()
                                .setSkusList(iap)
                                .setType(BillingClient.SkuType.INAPP)
                                .build()
                        )
                    }
                    details.skuDetailsList?.let { skuDetailsList ->
                        AlertDialog.Builder(this@DonateActivity)
                            .setItems(skuDetailsList.map { it.price }.toTypedArray()) { _, index ->
                                val responseCode = billingClient.launchBillingFlow(
                                    this@DonateActivity,
                                    BillingFlowParams.newBuilder().setSkuDetails(skuDetailsList[index]).build()
                                ).responseCode

                                if (responseCode != BillingClient.BillingResponseCode.OK) {
                                    Timber.e("Billing flow failed with response code $responseCode")
                                    finishWithErrorToast()
                                }
                            }
                            .setTitle(R.string.dialog_donate)
                            .setOnCancelListener {
                                finish()
                            }
                            .show()
                    }}
                }
            })
        }
}