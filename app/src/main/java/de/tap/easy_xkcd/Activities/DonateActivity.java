/**
 * *******************************************************************************
 * Copyright 2016 Tom Praschan
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ******************************************************************************
 */

package de.tap.easy_xkcd.Activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.widget.Toast;

import com.anjlab.android.iab.v3.BillingProcessor;
import com.anjlab.android.iab.v3.TransactionDetails;
import com.tap.xkcd_reader.R;

import de.tap.easy_xkcd.utils.PrefHelper;

public class DonateActivity extends AppCompatActivity {

    private BillingProcessor mBillingProcessor;
    private PrefHelper prefHelper;
    private static final String publicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApIdLEFtE9/AaPdPDMdFN3DJKKc0MZFyGJdTssBgFKlIi0VgpeocVgi9LJ4ev1P1OJExRp+P+X/3zMt2Z28s7gyBW+vtBKRJqdw8ix+mAtxGW81QFcwKCUW2nfn3pq2G7IXj0l/acfLruAfLyKwdpwzX/jqCJCFeolTRQxTDkisrxV25ShMFSlWCE9uvRwezs9v6GJ+5ebu+y632N9I2UVa+QIGQo2Kdgg1Iv2jEGihokGxbBOENxAggD8y4Ut66KNPE8gOo7r5/cFnAruLInSXqp9odHvF0JGTgrEereE9cCLGZG9/e+F3NE3T0DVd25ICVDq2IvVVcCyJT6WDFLZwIDAQAB";
    private static final String iap1 = "de.tap.easy_xkcd.iap1";
    private static final String iap2 = "de.tap.easy_xkcd.iap2";
    private static final String iap3 = "de.tap.easy_xkcd.iap3";
    private static final String iap4 = "de.tap.easy_xkcd.iap4";
    private static final String iap5 = "de.tap.easy_xkcd.iap5";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_donate);
        prefHelper = new PrefHelper(this);

        mBillingProcessor = new BillingProcessor(this, publicKey, new BillingProcessor.IBillingHandler() {
            @Override
            public void onBillingInitialized() {
                String[] mPrices = new String[5];
                mPrices[0] = mBillingProcessor.getPurchaseListingDetails(iap1).priceText;
                mPrices[1] = mBillingProcessor.getPurchaseListingDetails(iap2).priceText;
                mPrices[2] = mBillingProcessor.getPurchaseListingDetails(iap3).priceText;
                mPrices[3] = mBillingProcessor.getPurchaseListingDetails(iap4).priceText;
                mPrices[4] = mBillingProcessor.getPurchaseListingDetails(iap5).priceText;

                final String[] mIds = new String[5];
                mIds[0] = iap1;
                mIds[1] = iap2;
                mIds[2] = iap3;
                mIds[3] = iap4;
                mIds[4] = iap5;

                AlertDialog.Builder builder = new AlertDialog.Builder(DonateActivity.this);
                builder.setItems(mPrices, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        mBillingProcessor.purchase(DonateActivity.this, mIds[i]);
                    }
                })
                        .setTitle(R.string.dialog_donate)
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                DonateActivity.this.finish();
                            }
                        })
                        .show();
            }

            @Override
            public void onBillingError(int i, Throwable throwable) {
                DonateActivity.this.finish();
            }

            @Override
            public void onProductPurchased(String productId, TransactionDetails transactionDetails) {
                mBillingProcessor.consumePurchase(productId);
                prefHelper.setHideDonate(true);
                DonateActivity.this.finish();
                Toast.makeText(DonateActivity.this, "Thanks :)", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onPurchaseHistoryRestored() {
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (!mBillingProcessor.handleActivityResult(requestCode, resultCode, data))
            super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onDestroy() {
        if (mBillingProcessor != null)
            mBillingProcessor.release();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_donate, menu);
        return true;
    }

}
