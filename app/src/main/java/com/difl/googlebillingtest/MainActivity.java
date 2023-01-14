package com.difl.googlebillingtest;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    BillingClient billingClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        billingClient = BillingClient.newBuilder(this)
                .enablePendingPurchases()
                .setListener(new PurchasesUpdatedListener() {
                    @Override
                    public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> list) {
                        if(billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && list != null){
                            for(Purchase purchase: list){
                                if(purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED
                                        && !purchase.isAcknowledged()){
                                            verifyPurchase(purchase);
                                }
                            }
                        }
                    }
                })
                .build();

        connectToGooglePlayBilling();
    }

    @Override
    protected void onResume() {
        super.onResume();
        billingClient.queryPurchasesAsync(
                BillingClient.SkuType.INAPP,
                new PurchasesResponseListener() {
                    @Override
                    public void onQueryPurchasesResponse(@NonNull BillingResult billingResult, @NonNull List<Purchase> list) {
                        if(billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK){
                            for(Purchase purchase: list){
                                if(purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED &&
                                    !purchase.isAcknowledged()){
                                    verifyPurchase(purchase);
                                }
                            }
                        }
                    }
                }
        );
    }

    private void connectToGooglePlayBilling(){
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingServiceDisconnected() {
                connectToGooglePlayBilling();
            }

            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if(billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK){
                    getProductDetails();
                }
            }
        });
    }

    private void verifyPurchase(Purchase purchase){
        String requestUrl = "https://us-central1-playbilling-2cd7c.cloudfunctions.net/verifyPurchases?" +
                "purchaseToken=" + purchase.getPurchaseToken() + "&" +
                "purchaseTime=" + purchase.getPurchaseTime() + "&" +
                "orderId=" + purchase.getOrderId();

        StringRequest stringRequest = new StringRequest(
                Request.Method.POST,
                requestUrl,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try{
                            JSONObject purchaseInfoFromServer = new JSONObject(response);
                            if(purchaseInfoFromServer.getBoolean("isValid")){
                                AcknowledgePurchaseParams acknowledgePurchaseParams =
                                        AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.getPurchaseToken()).build();
                                billingClient.acknowledgePurchase(
                                        acknowledgePurchaseParams, new AcknowledgePurchaseResponseListener() {
                                            @Override
                                            public void onAcknowledgePurchaseResponse(@NonNull BillingResult billingResult) {
                                                if(billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK){
                                                    Toast.makeText(MainActivity.this, "PURCHASE COMPLETED!", Toast.LENGTH_LONG).show();
                                                }
                                            }
                                        }
                                );
                            }
                        }
                        catch (Exception err){}
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {

                    }
                }
        );

        Volley.newRequestQueue(this).add(stringRequest);
    }

    private void getProductDetails(){
        List<String> productsIds = new ArrayList<>();
        productsIds.add("test_billing");
        productsIds.add("test_billing_2");

        SkuDetailsParams getProductDetailsQuery = SkuDetailsParams.newBuilder()
                .setSkusList(productsIds)
                .setType(BillingClient.SkuType.INAPP)
                .build();

        Activity activity = this;
        billingClient.querySkuDetailsAsync(
                getProductDetailsQuery,
                new SkuDetailsResponseListener() {
                    @Override
                    public void onSkuDetailsResponse(@NonNull BillingResult billingResult, @Nullable List<SkuDetails> list) {
                        if(billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK &&
                            list != null){
                            TextView itemName = findViewById(R.id.itemName);
                            Button itemPrice = findViewById(R.id.itemPrice);
                            SkuDetails itemInfo = list.get(0);
                            itemName.setText(itemInfo.getTitle());
                            itemPrice.setText(itemInfo.getPrice());

                            itemPrice.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    billingClient.launchBillingFlow(
                                            activity, BillingFlowParams.newBuilder()
                                                    .setSkuDetails(itemInfo).build()
                                    );
                                }
                            });
                        }
                    }
                }
        );
    }

}