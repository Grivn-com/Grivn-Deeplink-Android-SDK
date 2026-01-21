package com.osdl.dynamiclinks

public sealed class DynamicLinksSDKError protected constructor(message: String? = null) :
    Exception(message) {
    public object DelegateUnavailable : DynamicLinksSDKError("Delegate not set")
    public object InvalidDynamicLink : DynamicLinksSDKError("Link is invalid")
    public object UnknownDelegateResponse :
        DynamicLinksSDKError("Delegate returned nil for both URL and error")
}
