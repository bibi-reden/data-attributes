package com.bibireden.data_attributes.config
import com.bibireden.data_attributes.api.attribute.StackingBehavior
import com.bibireden.data_attributes.config.functions.AttributeFunction
import com.bibireden.data_attributes.config.functions.AttributeFunctionConfig
import com.bibireden.data_attributes.config.models.OverridesConfigModel.AttributeOverride
import com.bibireden.data_attributes.data.EntityTypeData
import net.minecraft.entity.EntityType
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

private operator fun <T> Registry<T>.get(link: T) = this.getId(link)

object ConfigDefaults {
    private val MAX_HEALTH_ID = Registries.ATTRIBUTE[EntityAttributes.GENERIC_MAX_HEALTH]!!
    private val GENERIC_ARMOR_ID = Registries.ATTRIBUTE[EntityAttributes.GENERIC_ARMOR]!!

    @JvmField
    val OVERRIDES: Map<Identifier, AttributeOverride> = mapOf(
        MAX_HEALTH_ID to AttributeOverride(
            false,
            0.0,
            1024.0,
            0.0,
            1.0,
            1024.0
        )
    )

    @JvmField
    val FUNCTIONS: AttributeFunctionConfig = AttributeFunctionConfig(mapOf(
        GENERIC_ARMOR_ID to listOf(
            AttributeFunction(
                MAX_HEALTH_ID,
                StackingBehavior.Add,
                1.0,
            )
        )
    ))

    @JvmField
    val ENTITY_TYPES: Map<Identifier, EntityTypeData> = mapOf(
        Registries.ENTITY_TYPE[EntityType.PLAYER]!! to EntityTypeData(mutableMapOf(
            MAX_HEALTH_ID to EntityAttributes.GENERIC_MAX_HEALTH.defaultValue
        ))
    )
}