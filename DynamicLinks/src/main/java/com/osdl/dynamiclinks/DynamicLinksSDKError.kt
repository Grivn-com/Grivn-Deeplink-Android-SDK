package com.osdl.dynamiclinks

public sealed class DynamicLinksSDKError protected constructor(
    message: String? = null,
    cause: Throwable? = null
) : Exception(message, cause) {
    
    /** SDK has not been initialized. */
    public object NotInitialized : DynamicLinksSDKError("SDK not initialized. Call DynamicLinksSDK.init() first.")
    
    /** The dynamic link is invalid. */
    public object InvalidDynamicLink : DynamicLinksSDKError("Link is invalid")
    
    /** Project ID has not been set (required when creating links). */
    public object ProjectIdNotSet : DynamicLinksSDKError("Project ID not set. Call init() with projectId or setProjectId() or pass projectId to shorten().")
    
    /** Network error. */
    public class NetworkError(message: String, cause: Throwable?) : 
        DynamicLinksSDKError(message, cause)
    
    /** Server returned an error. */
    public class ServerError(message: String, public val code: Int) : 
        DynamicLinksSDKError("Server error ($code): $message")
    
    /** Failed to parse the response. */
    public class ParseError(message: String, cause: Throwable?) : 
        DynamicLinksSDKError(message, cause)
}
