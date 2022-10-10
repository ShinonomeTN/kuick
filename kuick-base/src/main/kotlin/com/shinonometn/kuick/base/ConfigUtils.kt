package com.shinonometn.kuick.base

import com.typesafe.config.Config
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueType

fun ConfigValue.stringValue() = if(valueType() == ConfigValueType.NULL) null else render(ConfigRenderOptions.concise())

fun Config.getValueOrNull(path : String) : ConfigValue? = if (!hasPath(path)) null else getValue(path)