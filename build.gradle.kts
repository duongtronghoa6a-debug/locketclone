// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // giữ alias nếu bạn đang dùng version catalog (libs.**)
    alias(libs.plugins.android.application) apply false
    id("com.google.gms.google-services") version "4.4.4" apply false
}
