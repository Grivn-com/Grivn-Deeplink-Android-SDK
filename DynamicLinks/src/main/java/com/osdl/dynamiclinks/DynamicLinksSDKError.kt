package com.osdl.dynamiclinks

public sealed class DynamicLinksSDKError protected constructor(
    message: String? = null,
    cause: Throwable? = null
) : Exception(message, cause) {
    
    /** SDK 未初始化 */
    public object NotInitialized : DynamicLinksSDKError("SDK not initialized. Call DynamicLinksSDK.init() first.")
    
    /** 无效的动态链接 */
    public object InvalidDynamicLink : DynamicLinksSDKError("Link is invalid")
    
    /** 项目 ID 未设置（创建链接时需要） */
    public object ProjectIdNotSet : DynamicLinksSDKError("Project ID not set. Call init() with projectId or setProjectId() or pass projectId to shorten().")
    
    /** 网络错误 */
    public class NetworkError(message: String, cause: Throwable?) : 
        DynamicLinksSDKError(message, cause)
    
    /** 服务器返回错误 */
    public class ServerError(message: String, public val code: Int) : 
        DynamicLinksSDKError("Server error ($code): $message")
    
    /** 解析响应失败 */
    public class ParseError(message: String, cause: Throwable?) : 
        DynamicLinksSDKError(message, cause)
}
