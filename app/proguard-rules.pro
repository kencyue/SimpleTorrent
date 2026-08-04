# jlibtorrent 用了大量 JNI,混淆會讓 native 呼叫失效,直接保留整個套件
-keep class com.frostwire.jlibtorrent.** { *; }
-dontwarn com.frostwire.jlibtorrent.**
