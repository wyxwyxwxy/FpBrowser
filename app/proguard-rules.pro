# WebView 的 JavaScript Bridge 类不可混淆（方法名需与 @JavascriptInterface 对应）
-keepclassmembers class com.fpbrowser.app.web.FpJsBridge {
    public *;
}
-keepattributes *Annotation*
# Room / Gson 模型保留
-keep class com.fpbrowser.app.data.** { *; }
