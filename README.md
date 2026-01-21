# DynamicLinks-Android

Android SDK for handling Dynamic Links with Grivn backend.

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.osdl:dynamiclinks:1.0.0")
}
```

## Quick Start

### 1. Initialize the SDK

Call `DynamicLinksSDK.init()` in your Application class or before using any SDK features:

```kotlin
import com.osdl.dynamiclinks.DynamicLinksSDK

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize SDK
        DynamicLinksSDK.init(
            baseUrl = "https://api.grivn.com",    // Backend API URL
            secretKey = "your_secret_key",         // X-API-Key for authentication
            projectId = "your_project_id"          // Project ID for creating links
        )
        
        // Configure allowed hosts for link validation
        DynamicLinksSDK.configure(
            allowedHosts = listOf("acme.wayp.link", "preview.acme.wayp.link")
        )
    }
}
```

### 2. Handle Dynamic Links

Handle incoming dynamic links in your Activity:

```kotlin
import com.osdl.dynamiclinks.DynamicLinksSDK

class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch {
            try {
                val dynamicLink = DynamicLinksSDK.handleDynamicLink(intent)
                
                // Get the deep link URL
                val deepLink = dynamicLink.url
                
                // Get UTM parameters
                val utmSource = dynamicLink.utmParameters["utm_source"]
                
                // Get minimum app version
                val minVersion = dynamicLink.minimumAppVersion
                
                // Navigate based on the deep link
                handleDeepLink(deepLink)
                
            } catch (e: DynamicLinksSDKError) {
                // Handle error
                Log.e("DynamicLinks", "Error handling link", e)
            }
        }
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let {
            lifecycleScope.launch {
                val dynamicLink = DynamicLinksSDK.handleDynamicLink(it)
                handleDeepLink(dynamicLink.url)
            }
        }
    }
}
```

### 3. Create (Shorten) Dynamic Links

Create a short dynamic link from components:

```kotlin
import com.osdl.dynamiclinks.*

lifecycleScope.launch {
    try {
        val components = DynamicLinkComponents(
            link = Uri.parse("https://myapp.com/product/123"),
            domainUriPrefix = "https://acme.wayp.link",
            androidParameters = AndroidParameters(
                packageName = "com.myapp.android",
                fallbackURL = "https://play.google.com/store/apps/details?id=com.myapp.android",
                minimumVersion = 10
            ),
            iOSParameters = IosParameters(
                appStoreID = "123456789",
                fallbackURL = "https://apps.apple.com/app/id123456789"
            ),
            analyticsParameters = AnalyticsParameters(
                source = "email",
                medium = "newsletter",
                campaign = "summer_sale"
            ),
            socialMetaTagParameters = SocialMetaTagParameters(
                title = "Check out this product!",
                descriptionText = "Amazing product on sale",
                imageURL = "https://myapp.com/product/123/image.jpg"
            )
        )
        
        val response = DynamicLinksSDK.shorten(components)
        
        // Get the short link
        val shortLink = response.shortLink
        Log.d("DynamicLinks", "Short link: $shortLink")
        
    } catch (e: DynamicLinksSDKError) {
        Log.e("DynamicLinks", "Error creating link", e)
    }
}
```

## API Reference

### DynamicLinksSDK

| Method | Description |
|--------|-------------|
| `init(baseUrl, secretKey, projectId)` | Initialize the SDK with backend credentials |
| `configure(allowedHosts)` | Set allowed hosts for link validation |
| `setTrustAllCerts(enabled)` | Trust all SSL certificates (dev only) |
| `handleDynamicLink(intent/uri)` | Parse a dynamic link and return `DynamicLink` |
| `shorten(components)` | Create a short link from `DynamicLinkComponents` |
| `isValidDynamicLink(intent/uri)` | Check if a link is a valid dynamic link |

### DynamicLinkComponents

Parameters for creating a dynamic link:

| Parameter | Type | Description |
|-----------|------|-------------|
| `link` | `Uri` | Target deep link URL |
| `domainUriPrefix` | `String` | Short link domain prefix |
| `androidParameters` | `AndroidParameters?` | Android-specific settings |
| `iOSParameters` | `IosParameters` | iOS-specific settings |
| `analyticsParameters` | `AnalyticsParameters?` | UTM tracking parameters |
| `socialMetaTagParameters` | `SocialMetaTagParameters?` | Social sharing preview |
| `iTunesConnectParameters` | `ItunesConnectAnalyticsParameters?` | iTunes affiliate tracking |
| `otherPlatformParameters` | `OtherPlatformParameters?` | Desktop fallback URL |
| `options` | `DynamicLinkOptionsParameters` | Link options (path length) |

### DynamicLink

Parsed dynamic link result:

| Property | Type | Description |
|----------|------|-------------|
| `url` | `Uri` | The deep link URL |
| `utmParameters` | `Map<String, String>` | UTM tracking parameters |
| `minimumAppVersion` | `Int?` | Minimum required app version |

### Error Handling

```kotlin
try {
    val link = DynamicLinksSDK.handleDynamicLink(intent)
} catch (e: DynamicLinksSDKError) {
    when (e) {
        is DynamicLinksSDKError.NotInitialized -> {
            // SDK not initialized, call init() first
        }
        is DynamicLinksSDKError.InvalidDynamicLink -> {
            // Link is not a valid dynamic link
        }
        is DynamicLinksSDKError.NetworkError -> {
            // Network request failed
        }
        is DynamicLinksSDKError.ServerError -> {
            // Server returned an error (check e.code)
        }
        is DynamicLinksSDKError.ParseError -> {
            // Failed to parse server response
        }
    }
}
```

## Development Setup

For development/testing with self-signed certificates:

```kotlin
DynamicLinksSDK
    .setTrustAllCerts(true)  // ⚠️ Only for development!
    .init(
        baseUrl = "https://localhost:8080",
        secretKey = "dev_secret_key",
        projectId = "test_project"
    )
```

## License

MIT License - see [LICENSE](LICENSE) for details.
