package com.shinonometn.kuick.platform.settings

interface PlatformSettingStorage {
    fun set(key : String, value : String?)
    fun get(key : String) : String?
}