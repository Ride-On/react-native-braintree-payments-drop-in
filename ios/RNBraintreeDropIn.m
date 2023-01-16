#import "RNBraintreeDropIn.h"
@import PassKit;
#import "BTThreeDSecureRequest.h"
#import "BTUIKAppearance.h"


@interface RNBraintreeDropIn()

@property (nonatomic, strong) BTAPIClient *apiClient;
@property (nonatomic, strong) RCTPromiseResolveBlock resolve;
@property (nonatomic, strong) RCTPromiseRejectBlock reject;

@end

@implementation RNBraintreeDropIn

- (dispatch_queue_t)methodQueue
{
    return dispatch_get_main_queue();
}
RCT_EXPORT_MODULE()

RCT_REMAP_METHOD(show,
                 showWithOptions:(NSDictionary*)options resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    NSString* clientToken = options[@"clientToken"];
    if (!clientToken) {
        reject(@"NO_CLIENT_TOKEN", @"You must provide a client token", nil);
        return;
    }

    BTUIKAppearance.sharedInstance.postalCodeFormFieldKeyboardType = UIKeyboardTypeNamePhonePad;
    
    NSDictionary* threeDSecureOptions = options[@"threeDSecure"];

    BTThreeDSecureRequest *threeDSecureRequest = [[BTThreeDSecureRequest alloc] init];
    threeDSecureRequest.amount = [NSDecimalNumber decimalNumberWithString:threeDSecureOptions[@"amount"]];
    threeDSecureRequest.versionRequested = BTThreeDSecureVersion2;
    threeDSecureRequest.challengeRequested = YES;
    
    BTDropInRequest *request = [[BTDropInRequest alloc] init];
    request.cardholderNameSetting = BTFormFieldRequired;

    if (!options[@"disableVaultManager"]) {
        request.vaultManager = YES;
    }
    
    if (!options[@"disableVaultCard"]) {
        request.vaultCard = YES;
    }
    
    /*if (!options[@"validate"]) {
        request.shouldValidate = YES;
    }*/
    
    if (!options[@"disableAllowVaultCardOverride"]) {
        request.allowVaultCardOverride = YES;
    }
    
    
    request.threeDSecureRequest = threeDSecureRequest;


    self.apiClient = [[BTAPIClient alloc] initWithAuthorization:clientToken];


    BTDropInController *dropIn = [[BTDropInController alloc] initWithAuthorization:clientToken request:request handler:^(BTDropInController * _Nonnull controller, BTDropInResult * _Nullable result, NSError * _Nullable error) {
            [self.reactRoot dismissViewControllerAnimated:YES completion:nil];

            if (error != nil) {
                reject(error.localizedDescription, error.localizedDescription, error);
            } else if (result.canceled) {
                reject(@"USER_CANCELLATION", @"The user cancelled", nil);
            } else {
                if (threeDSecureOptions && [result.paymentMethod isKindOfClass:[BTCardNonce class]]) {
                    BTCardNonce *cardNonce = (BTCardNonce *)result.paymentMethod;
                    if (!cardNonce.threeDSecureInfo.liabilityShiftPossible && cardNonce.threeDSecureInfo.wasVerified) {
                        reject(@"3DSECURE_NOT_ABLE_TO_SHIFT_LIABILITY", @"3D Secure liability cannot be shifted", nil);
                    } else if (!cardNonce.threeDSecureInfo.liabilityShifted && cardNonce.threeDSecureInfo.wasVerified) {
                        reject(@"3DSECURE_LIABILITY_NOT_SHIFTED", @"3D Secure liability was not shifted", nil);
                    } else {
                        [[self class] resolvePayment: result
                                            resolver: resolve
                                       ];
                    }
                }
            }
        }];

    if (dropIn != nil) {
        [self.reactRoot presentViewController:dropIn animated:YES completion:nil];
    } else {
        reject(@"INVALID_CLIENT_TOKEN", @"The client token seems invalid", nil);
    }
}

+ (void)resolvePayment:(BTDropInResult* _Nullable)result
              resolver:(RCTPromiseResolveBlock _Nonnull)resolve
          {
    NSMutableDictionary* jsResult = [NSMutableDictionary new];
    [jsResult setObject:result.paymentMethod.nonce forKey:@"nonce"];
    [jsResult setObject:result.paymentMethod.type forKey:@"type"];
    [jsResult setObject:result.paymentDescription forKey:@"description"];
    [jsResult setObject:[NSNumber numberWithBool:result.paymentMethod.isDefault] forKey:@"isDefault"];
              resolve(jsResult);
}

- (UIViewController*)reactRoot {
    UIViewController *root  = [UIApplication sharedApplication].keyWindow.rootViewController;
    UIViewController *maybeModal = root.presentedViewController;

    UIViewController *modalRoot = root;

    if (maybeModal != nil) {
        modalRoot = maybeModal;
    }

    return modalRoot;
}

@end
