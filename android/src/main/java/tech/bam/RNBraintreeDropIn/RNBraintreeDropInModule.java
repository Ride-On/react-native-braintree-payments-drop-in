package tech.bam.RNBraintreeDropIn;

import android.app.Activity;
import android.content.Intent;
import androidx.fragment.app.FragmentActivity;
import com.braintreepayments.api.DropInClient;
import com.braintreepayments.api.ThreeDSecureRequest;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.ActivityEventListener;
import com.braintreepayments.api.DropInRequest;
import com.braintreepayments.api.DropInResult;
import com.braintreepayments.api.CardNonce;
import com.braintreepayments.api.PaymentMethodNonce;
import com.facebook.react.bridge.Promise;
import com.braintreepayments.cardform.view.CardForm;

public class RNBraintreeDropInModule extends ReactContextBaseJavaModule implements  ActivityEventListener {

    private Promise mPromise;
    private String mClientToken;
    private static final int DROP_IN_REQUEST = 0x444;
    private ThreeDSecureRequest threeDSecureRequest;
    private DropInRequest dropInRequest;
    private DropInClient dropInClient;
    private FragmentActivity mCurrentActivity;
    private Activity mActivity;
    private final ReactApplicationContext reactContext;
    private PaymentMethodNonce nonce;

    @Override
    public String getName() {
        return "RNBraintreeDropIn";
    }

    RNBraintreeDropInModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        reactContext.addActivityEventListener(this);
    }


    @ReactMethod
    public void show(final ReadableMap options, final Promise promise) {
        if (!options.hasKey("clientToken")) {
            promise.reject("NO_CLIENT_TOKEN", "You must provide a client token");
            return;
        } else {
            mClientToken = options.getString("clientToken");
        }

         mCurrentActivity = (FragmentActivity) getCurrentActivity();
         mActivity = (Activity) reactContext.getCurrentActivity();
        if (mActivity == null) {
            promise.reject("NO_ACTIVITY", "There is no current activity");
            return;
        } else {
            boolean disableVaultManager = !options.hasKey("disableVaultManager")
                    || (options.hasKey("disableVaultManager")
                    && !options.getBoolean("disableVaultManager"));

            boolean disableVaultCard = !options.hasKey("disableVaultCard")
                    || (options.hasKey("disableVaultCard")
                    && !options.getBoolean("disableVaultCard"));

            boolean disableAllowVaultCardOverride = !options.hasKey("disableAllowVaultCardOverride")
                    || (options.hasKey("disableAllowVaultCardOverride")
                    && !options.getBoolean("disableAllowVaultCardOverride"));


            final ReadableMap threeDSecureOptions = options.getMap("threeDSecure");
            if (threeDSecureOptions == null) {
                promise.reject("THREEDSECURE_IS_NULL", "3D Secure options were not provided");
                return;
            }

            threeDSecureRequest = new ThreeDSecureRequest();
            threeDSecureRequest.setAmount(threeDSecureOptions.getString("amount"));
            threeDSecureRequest.setVersionRequested(ThreeDSecureRequest.VERSION_2);
            threeDSecureRequest.setChallengeRequested(true);

            dropInRequest = new DropInRequest();
            dropInRequest.setAllowVaultCardOverride(disableAllowVaultCardOverride);
            dropInRequest.setVaultCardDefaultValue(disableVaultCard);
            dropInRequest.setVaultManagerEnabled(disableVaultManager);
            dropInRequest.setCardholderNameStatus(CardForm.FIELD_REQUIRED);
            dropInRequest.setThreeDSecureRequest(threeDSecureRequest);

            dropInClient = new DropInClient(reactContext, mClientToken, dropInRequest);
            dropInClient.launchDropInForResult(mCurrentActivity, DROP_IN_REQUEST);
            mPromise = promise;
        }

    }
    private void resolvePayment(PaymentMethodNonce paymentMethodNonce) {
        try {
            WritableMap jsResult = Arguments.createMap();
            jsResult.putString("nonce", paymentMethodNonce.getString());
            jsResult.putBoolean("isDefault", paymentMethodNonce.isDefault());
            mPromise.resolve(jsResult);
            mPromise = null;
        } catch (NullPointerException ignore) {
            mPromise.reject("PAYMENT_NONCE_RESOLVE_FAILED", "Failed to resolve payment nonce");
            mPromise = null;
        }
    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {

           if (requestCode != DROP_IN_REQUEST || mPromise == null) {
               return;
           }

        if (requestCode == DROP_IN_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                DropInResult result = data.getParcelableExtra(DropInResult.EXTRA_DROP_IN_RESULT);

                 nonce = result.getPaymentMethodNonce();
                if (nonce instanceof CardNonce) {
                    CardNonce cardNonce = (CardNonce) nonce;
                    if (!cardNonce.getThreeDSecureInfo().isLiabilityShiftPossible()) {
                        mPromise.reject("3DSECURE_NOT_ABLE_TO_SHIFT_LIABILITY", "3D Secure liability cannot be shifted");
                        return;
                    } else if (!cardNonce.getThreeDSecureInfo().isLiabilityShifted()) {
                        mPromise.reject("3DSECURE_LIABILITY_NOT_SHIFTED", "3D Secure liability was not shifted");
                        return;
                    }
                }
               resolvePayment(nonce);
            } else if (resultCode == Activity.RESULT_CANCELED) {
                mPromise.reject("USER_CANCELLATION", "The user cancelled");
                mPromise = null;
            } else {
                // an error occurred, checked the returned exception
                Exception exception = (Exception) data.getSerializableExtra(DropInResult.EXTRA_ERROR);
                mPromise.reject(exception.getMessage(), exception.getMessage());
                mPromise = null;
            }
        }

    }

    @Override
    public void onNewIntent(Intent intent) {
        if (mCurrentActivity != null) {
            mCurrentActivity.setIntent(intent);
        }
    }

}
