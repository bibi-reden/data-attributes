package com.bibireden.data_attributes.config

import blue.endless.jankson.Comment
import com.bibireden.data_attributes.DataAttributes
import io.wispforest.owo.config.Option.SyncMode
import io.wispforest.owo.config.annotation.Config
import io.wispforest.owo.config.annotation.ExcludeFromScreen
import io.wispforest.owo.config.annotation.Sync

@Suppress("UNUSED")
@Config(name = DataAttributes.MOD_ID, wrapperName = "DataAttributesConfig")
class DataAttributesConfigModel {
    @JvmField
    @ExcludeFromScreen
    @Sync(SyncMode.OVERRIDE_CLIENT)
    var locked: Boolean = true
}