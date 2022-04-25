package tech.bam.RNBraintreeDropIn;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;


import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.Promise;


import com.braintreepayments.api.CardNonce;
import com.braintreepayments.api.DropInClient;
import com.braintreepayments.api.DropInPaymentMethod;
import com.braintreepayments.api.DropInRequest;
import com.braintreepayments.api.DropInResult;
import com.braintreepayments.api.BraintreeClient;
import com.braintreepayments.api.DataCollector;
import com.braintreepayments.api.ThreeDSecureInfo;
import com.braintreepayments.api.PaymentMethodNonce;
import com.braintreepayments.api.ThreeDSecureRequest;
import com.braintreepayments.cardform.view.CardForm;
import com.braintreepayments.api.InvalidArgumentException;


public class RNBraintreeDropInModule extends ReactContextBaseJavaModule {

    private Promise mPromise;
    private String mClientToken;
    private static final int DROP_IN_REQUEST = 0x444;
    private PaymentMethodNonce nonce;
    private BraintreeClient braintreeClient;
    private DataCollector dataCollector;

    RNBraintreeDropInModule(ReactApplicationContext reactContext) {
        super(reactContext);
        reactContext.addActivityEventListener(new BaseActivityEventListener() {
            @Override
            public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
                super.onActivityResult(activity, requestCode, resultCode, data);

                if (requestCode != DROP_IN_REQUEST || mPromise == null) {
                    return;
                }

                if (resultCode == Activity.RESULT_OK) {
                    DropInResult result = data.getParcelableExtra(DropInResult.EXTRA_DROP_IN_RESULT);
                    nonce = result.getPaymentMethodNonce();

                    if (nonce instanceof CardNonce) {
                        CardNonce cardNonce = (CardNonce) nonce;
                        ThreeDSecureInfo threeDSecureInfo = cardNonce.getThreeDSecureInfo();
                        resolvePayment(result, activity);

                        if (!threeDSecureInfo.isLiabilityShiftPossible()) {
                            mPromise.reject("3DSECURE_NOT_ABLE_TO_SHIFT_LIABILITY", "3D Secure liability cannot be shifted");
                            return;
                        } else if (!threeDSecureInfo.isLiabilityShifted()) {
                            mPromise.reject("3DSECURE_LIABILITY_NOT_SHIFTED", "3D Secure liability was not shifted");
                            return;
                        }
                    }


                } else if (resultCode == Activity.RESULT_CANCELED) {
                    mPromise.reject("USER_CANCELLATION", "The user cancelled");
                    mPromise = null;
                } else {
                    Exception exception = (Exception) data.getSerializableExtra(DropInResult.EXTRA_ERROR);
                    mPromise.reject(exception.getMessage(), exception.getMessage());
                    mPromise = null;
                }

            }
        });
    }

    @ReactMethod
    public void show(final ReadableMap options, final Promise promise) {

        if (!options.hasKey("clientToken")) {
            promise.reject("NO_CLIENT_TOKEN", "You must provide a client token");
            return;
        } else {
            mClientToken = options.getString("clientToken");
        }

        Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            promise.reject("NO_ACTIVITY", "There is no current activity");
            return;
        }

        boolean disableVaultManager = !options.hasKey("disableVaultManager")
                || (options.hasKey("disableVaultManager")
                && !options.getBoolean("disableVaultManager"));

        boolean disableVaultCard = !options.hasKey("disableVaultCard")
                || (options.hasKey("disableVaultCard")
                && !options.getBoolean("disableVaultCard"));

        boolean disableAllowVaultCardOverride = !options.hasKey("disableAllowVaultCardOverride")
                || (options.hasKey("disableAllowVaultCardOverride")
                && !options.getBoolean("disableAllowVaultCardOverride"));

        /*boolean validate = !options.hasKey("validate")
                || (options.hasKey("disabledValidate")
                && !options.getBoolean("disabledValidate"));*/


        try {

            final ReadableMap threeDSecureOptions = options.getMap("threeDSecure");

            DropInRequest dropInRequest = new DropInRequest();

            dropInRequest.setMaskCardNumber(true);
            dropInRequest.setMaskSecurityCode(true);
            dropInRequest.setAllowVaultCardOverride(false);
            dropInRequest.setVaultCardDefaultValue(false);
            dropInRequest.setVaultManagerEnabled(false);
            dropInRequest.setCardholderNameStatus(CardForm.FIELD_REQUIRED);

            if (threeDSecureOptions == null) {
                promise.reject("THREEDSECURE_IS_NULL", "3D Secure options were not provided");
                return;
            } else {

                try {

                    ThreeDSecureRequest threeDSecureRequest = new ThreeDSecureRequest();
                    threeDSecureRequest.setAmount(threeDSecureOptions.getString("amount"));
                    threeDSecureRequest.setVersionRequested(ThreeDSecureRequest.VERSION_2);
                    threeDSecureRequest.setChallengeRequested(true);

                    dropInRequest.setThreeDSecureRequest(threeDSecureRequest);

                } catch (Exception error) {
                    promise.reject("THREEDSECURE_FAILED", error.getMessage());
                    return;
                }

            }
    
            DropInClient dropInClient = new DropInClient(currentActivity, mClientToken, dropInRequest);
            dropInClient.launchDropInForResult((FragmentActivity) currentActivity, DROP_IN_REQUEST);

        } catch (Exception error) {
            promise.reject("DROP_IN_FAILED", error.getMessage());
            return;
        }

        mPromise = promise;

    }


    private void resolvePayment(DropInResult dropInResult, Activity currentActivity) {

        try {
            nonce = dropInResult.getPaymentMethodNonce();
            DropInPaymentMethod paymentMethodType = dropInResult.getPaymentMethodType();
            WritableMap jsResult = Arguments.createMap();
            jsResult.putString("nonce", nonce.getString());
            jsResult.putString("type", String.valueOf(paymentMethodType.getLocalizedName()));
            jsResult.putString("description", dropInResult.getPaymentDescription());
            jsResult.putBoolean("isDefault", nonce.isDefault());
            extractDeviceData(currentActivity, jsResult);
        } catch (NullPointerException ignore) {
            mPromise.reject("PAYMENT_NONCE_RESOLVE_FAILED", "Failed to resolve payment nonce");
            mPromise = null;
        }
    }

    private void extractDeviceData(Activity currentActivity, final WritableMap jsResult) {

        if (currentActivity instanceof AppCompatActivity) {
            try {
                braintreeClient = new BraintreeClient(currentActivity, mClientToken);
                dataCollector = new DataCollector(braintreeClient);
                dataCollector.collectDeviceData(currentActivity, (deviceData, error) -> {
                    // send deviceData to your server
                    if (deviceData != null) {
                        jsResult.putString("deviceData", deviceData);
                    }
                    if (error != null) {
                        jsResult.putString("error", String.valueOf(error));
                    }
                    mPromise.resolve(jsResult);
                    mPromise = null;
                });
            } catch (NullPointerException ignore) {
                mPromise.reject("PAYMENT_NONCE_RESOLVE_FAILED", "Failed to resolve payment nonce");
            }
        } else {
            Log.e("DropInModule", "Failed to extract device data, activity is not AppCompat");
            mPromise.resolve(jsResult);
            mPromise = null;
        }
    }

    @NonNull
    @Override
    public String getName() {
        return "RNBraintreeDropIn";
    }
}
