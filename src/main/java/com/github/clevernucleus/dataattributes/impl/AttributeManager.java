package com.github.clevernucleus.dataattributes.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.clevernucleus.dataattributes.api.DataAttributesAPI;
import com.github.clevernucleus.dataattributes.api.event.AttributesReloadedEvent;
import com.github.clevernucleus.dataattributes.json.AttributeOverrideJson;
import com.github.clevernucleus.dataattributes.json.EntityTypesJson;
import com.github.clevernucleus.dataattributes.json.AttributeFunctionJson;
import com.github.clevernucleus.dataattributes.json.FunctionsJson;
import com.github.clevernucleus.dataattributes.json.PropertiesJson;
import com.github.clevernucleus.dataattributes.mutable.MutableEntityAttribute;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.fabric.api.resource.SimpleResourceReloadListener;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.DefaultAttributeRegistry;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.registry.Registry;

public final class AttributeManager implements SimpleResourceReloadListener<AttributeManager.Wrapper> {
	private static final Gson GSON = (new GsonBuilder()).excludeFieldsWithoutExposeAnnotation().create();
	private static final int PATH_SUFFIX_LENGTH = ".json".length();
	private static final Logger LOGGER = LogManager.getLogger();
	private static final String DIRECTORY = "attributes";
	private static final Identifier ID = new Identifier(DataAttributesAPI.MODID, DIRECTORY);
	
	private Map<Identifier, EntityAttributeData> entityAttributeData = ImmutableMap.of();
	private Map<Identifier, EntityTypeData> entityTypeData = ImmutableMap.of();
	public Map<EntityType<? extends LivingEntity>, DefaultAttributeContainer> containers = ImmutableMap.of();
	
	protected static class Wrapper {
		public final Map<Identifier, EntityAttributeData> entityAttributeData;
		public final Map<Identifier, EntityTypeData> entityTypeData;
		
		public Wrapper(Map<Identifier, EntityAttributeData> entityAttributeData, Map<Identifier, EntityTypeData> entityTypeData) {
			this.entityAttributeData = entityAttributeData;
			this.entityTypeData = entityTypeData;
		}
	}
	
	public AttributeManager() {}
	
	private static Map<Identifier, AttributeFunctionJson> formatFunctions(Map<String, AttributeFunctionJson> functionsIn) {
		Map<Identifier, AttributeFunctionJson> functions = new HashMap<Identifier, AttributeFunctionJson>();
		
		for(String key : functionsIn.keySet()) {
			AttributeFunctionJson value = functionsIn.get(key);
			
			functions.put(new Identifier(key), value);
		}
		
		return functions;
	}
	
	private static EntityAttribute getOrCreate(final Identifier identifier, EntityAttribute attributeIn) {
		EntityAttribute attribute = Registry.ATTRIBUTE.get(identifier);
		
		if(attribute == null) {
			attribute = MutableRegistryImpl.register(Registry.ATTRIBUTE, identifier, attributeIn);
		}
		
		return attribute;
	}
	
	private static void loadOverrides(ResourceManager manager, Map<Identifier, EntityAttributeData> entityAttributeData) {
		Map<Identifier, AttributeOverrideJson> cache = new HashMap<Identifier, AttributeOverrideJson>();
		String location = DIRECTORY + "/overrides";
		int length = location.length() + 1;
		
		for(Identifier resource : manager.findResources(location, file -> file.endsWith(".json"))) {
			String path = resource.getPath();
			Identifier identifier = new Identifier(resource.getNamespace(), path.substring(length, path.length() - PATH_SUFFIX_LENGTH));
			
			try (
				Resource resourceStream = manager.getResource(resource);
				InputStream inputStream = resourceStream.getInputStream();
				Reader readerStream = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
			) {
				AttributeOverrideJson json = JsonHelper.deserialize(GSON, readerStream, AttributeOverrideJson.class);
				
				if(json != null) {
					AttributeOverrideJson object = cache.put(identifier, json);
					
					if(object != null) throw new IllegalStateException("Duplicate data file ignored with ID " + identifier);
				} else {
					LOGGER.error("Couldn't load data file {} from {} as it's null or empty", identifier, resource);
				}
			} catch(IOException exception) {
				LOGGER.error("Couldn't parse data file {} from {}", identifier, resource, exception);
			}
		}
		
		cache.forEach((key, value) -> entityAttributeData.put(key, new EntityAttributeData(value)));
	}
	
	private static void loadFunctions(ResourceManager manager, Map<Identifier, EntityAttributeData> entityAttributeData) {
		Map<Identifier, FunctionsJson> cache = new HashMap<Identifier, FunctionsJson>();
		int length = DIRECTORY.length() + 1;
		
		for(Identifier resource : manager.findResources(DIRECTORY, file -> file.endsWith("functions.json"))) {
			String path = resource.getPath();
			Identifier identifier = new Identifier(resource.getNamespace(), path.substring(length, path.length() - PATH_SUFFIX_LENGTH));
			
			try (
				Resource resourceStream = manager.getResource(resource);
				InputStream inputStream = resourceStream.getInputStream();
				Reader readerStream = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
			) {
				FunctionsJson json = JsonHelper.deserialize(GSON, readerStream, FunctionsJson.class);
				
				if(json != null) {
					FunctionsJson object = cache.put(identifier, json);
					
					if(object != null) throw new IllegalStateException("Duplicate data file ignored with ID " + identifier);
				} else {
					LOGGER.error("Couldn't load data file {} from {} as it's null or empty", identifier, resource);
				}
				
				resourceStream.close();
			} catch(IOException exception) {
				LOGGER.error("Couldn't parse data file {} from {}", identifier, resource, exception);
			}
		}
		
		Map<String, Map<String, AttributeFunctionJson>> functions = new HashMap<String, Map<String, AttributeFunctionJson>>();
		cache.values().forEach(json -> json.merge(functions));
		
		for(String key : functions.keySet()) {
			Identifier identifier = new Identifier(key);
			EntityAttributeData data = entityAttributeData.getOrDefault(identifier, new EntityAttributeData());
			data.putFunctions(formatFunctions(functions.get(key)));
			entityAttributeData.put(identifier, data);
		}
	}
	
	private static void loadProperties(ResourceManager manager, Map<Identifier, EntityAttributeData> entityAttributeData) {
		Map<Identifier, PropertiesJson> cache = new HashMap<Identifier, PropertiesJson>();
		int length = DIRECTORY.length() + 1;
		
		for(Identifier resource : manager.findResources(DIRECTORY, file -> file.endsWith("properties.json"))) {
			String path = resource.getPath();
			Identifier identifier = new Identifier(resource.getNamespace(), path.substring(length, path.length() - PATH_SUFFIX_LENGTH));
			
			try (
				Resource resourceStream = manager.getResource(resource);
				InputStream inputStream = resourceStream.getInputStream();
				Reader readerStream = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
			) {
				PropertiesJson json = JsonHelper.deserialize(GSON, readerStream, PropertiesJson.class);
				
				if(json != null) {
					PropertiesJson object = cache.put(identifier, json);
					
					if(object != null) throw new IllegalStateException("Duplicate data file ignored with ID " + identifier);
				} else {
					LOGGER.error("Couldn't load data file {} from {} as it's null or empty", identifier, resource);
				}
				
				resourceStream.close();
			} catch(IOException exception) {
				LOGGER.error("Couldn't parse data file {} from {}", identifier, resource, exception);
			}
		}
		
		Map<String, Map<String, String>> properties = new HashMap<String, Map<String, String>>();
		cache.values().forEach(json -> json.merge(properties));
		
		for(String key : properties.keySet()) {
			Identifier identifier = new Identifier(key);
			EntityAttributeData data = entityAttributeData.getOrDefault(identifier, new EntityAttributeData());
			data.putProperties(properties.get(key));
			entityAttributeData.put(identifier, data);
		}
	}
	
	private static void loadEntityTypes(ResourceManager manager, Map<Identifier, EntityTypeData> entityTypeData) {
		Map<Identifier, EntityTypesJson> cache = new HashMap<Identifier, EntityTypesJson>();
		int length = DIRECTORY.length() + 1;
		
		for(Identifier resource : manager.findResources(DIRECTORY, file -> file.endsWith("entity_types.json"))) {
			String path = resource.getPath();
			Identifier identifier = new Identifier(resource.getNamespace(), path.substring(length, path.length() - PATH_SUFFIX_LENGTH));
			
			try (
				Resource resourceStream = manager.getResource(resource);
				InputStream inputStream = resourceStream.getInputStream();
				Reader readerStream = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
			) {
				EntityTypesJson json = JsonHelper.deserialize(GSON, readerStream, EntityTypesJson.class);
				
				if(json != null) {
					EntityTypesJson object = cache.put(identifier, json);
					
					if(object != null) throw new IllegalStateException("Duplicate data file ignored with ID " + identifier);
				}
			} catch(IOException exception) {
				LOGGER.error("Couldn't parse data file {} from {}", identifier, resource, exception);
			}
		}
		
		Map<String, Map<String, Double>> entityTypes = new HashMap<String, Map<String, Double>>();
		cache.values().forEach(json -> json.merge(entityTypes));
		
		for(String key : entityTypes.keySet()) {
			Identifier identifier = new Identifier(key);
			EntityTypeData data = new EntityTypeData(entityTypes.get(key));
			entityTypeData.put(identifier, data);
		}
	}
	
	public DefaultAttributeContainer getContainer(EntityType<? extends LivingEntity> entityType) {
		return this.containers.getOrDefault(entityType, DefaultAttributeRegistry.get(entityType));
	}
	
	public void toNbt(NbtCompound tag) {
		NbtCompound entityAttributeNbt = new NbtCompound();
		NbtCompound entityTypeNbt = new NbtCompound();
		
		this.entityAttributeData.forEach((key, value) -> {
			NbtCompound entry = new NbtCompound();
			value.writeToNbt(entry);
			entityAttributeNbt.put(key.toString(), entry);
		});
		
		this.entityTypeData.forEach((key, value) -> {
			NbtCompound entry = new NbtCompound();
			value.writeToNbt(entry);
			entityTypeNbt.put(key.toString(), entry);
		});
		
		tag.put("Attributes", entityAttributeNbt);
		tag.put("EntityTypes", entityTypeNbt);
	}
	
	public void fromNbt(NbtCompound tag) {
		if(tag.contains("Attributes")) {
			ImmutableMap.Builder<Identifier, EntityAttributeData> builder = ImmutableMap.builder();
			NbtCompound nbtCompound = tag.getCompound("Attributes");
			nbtCompound.getKeys().forEach(key -> {
				NbtCompound entry = nbtCompound.getCompound(key);
				EntityAttributeData entityAttributeData = new EntityAttributeData();
				entityAttributeData.readFromNbt(entry);
				builder.put(new Identifier(key), entityAttributeData);
			});
			
			this.entityAttributeData = builder.build();
		}
		
		if(tag.contains("EntityTypes")) {
			ImmutableMap.Builder<Identifier, EntityTypeData> builder = ImmutableMap.builder();
			NbtCompound nbtCompound = tag.getCompound("EntityTypes");
			nbtCompound.getKeys().forEach(key -> {
				NbtCompound entry = nbtCompound.getCompound(key);
				EntityTypeData entityTypeData = new EntityTypeData();
				entityTypeData.readFromNbt(entry);
				builder.put(new Identifier(key), entityTypeData);
			});
			
			this.entityTypeData = builder.build();
		}
	}
	
	public void apply() {
		MutableRegistryImpl.unregister(Registry.ATTRIBUTE);
		
		for(Identifier identifier : Registry.ATTRIBUTE.getIds()) {
			EntityAttribute entityAttribute = Registry.ATTRIBUTE.get(identifier);
			
			if(entityAttribute == null) continue;
			
			((MutableEntityAttribute)entityAttribute).clear();
		}
		
		for(Identifier identifier : this.entityAttributeData.keySet()) {
			EntityAttributeData entityAttributeData = this.entityAttributeData.get(identifier);
			entityAttributeData.override(identifier, AttributeManager::getOrCreate);
		}
		
		for(Identifier identifier : this.entityAttributeData.keySet()) {
			EntityAttribute entityAttribute = Registry.ATTRIBUTE.get(identifier);
			
			if(entityAttribute == null) continue;
			
			EntityAttributeData entityAttributeData = this.entityAttributeData.get(identifier);
			entityAttributeData.copy(entityAttribute);
		}
		
		Collection<Identifier> entityTypes = Registry.ENTITY_TYPE.getIds().stream().filter(id -> DefaultAttributeRegistry.hasDefinitionFor(Registry.ENTITY_TYPE.get(id))).collect(Collectors.toSet());
		ImmutableMap.Builder<EntityType<? extends LivingEntity>, DefaultAttributeContainer> containers = ImmutableMap.builder();
		
		for(Identifier identifier : this.entityTypeData.keySet()) {
			if(!entityTypes.contains(identifier)) continue;
			
			@SuppressWarnings("unchecked")
			EntityType<? extends LivingEntity> entityType = (EntityType<? extends LivingEntity>)Registry.ENTITY_TYPE.get(identifier);
			DefaultAttributeContainer.Builder builder = DefaultAttributeContainer.builder();
			EntityTypeData entityTypeData = this.entityTypeData.get(identifier);
			entityTypeData.build(builder, DefaultAttributeRegistry.get(entityType));
			containers.put(entityType, builder.build());
		}
		
		this.containers = containers.build();
		
		AttributesReloadedEvent.EVENT.invoker().onCompletedReload();
	}
	
	@Override
	public CompletableFuture<AttributeManager.Wrapper> load(ResourceManager manager, Profiler profiler, Executor executor) {
		return CompletableFuture.supplyAsync(() -> {
			Map<Identifier, EntityAttributeData> entityAttributeData = new HashMap<Identifier, EntityAttributeData>();
			loadOverrides(manager, entityAttributeData);
			loadFunctions(manager, entityAttributeData);
			loadProperties(manager, entityAttributeData);
			
			Map<Identifier, EntityTypeData> entityTypeData = new HashMap<Identifier, EntityTypeData>();
			loadEntityTypes(manager, entityTypeData);
			
			return new AttributeManager.Wrapper(entityAttributeData, entityTypeData);
		}, executor);
	}
	
	@Override
	public CompletableFuture<Void> apply(AttributeManager.Wrapper data, ResourceManager manager, Profiler profiler, Executor executor) {
		return CompletableFuture.runAsync(() -> {
			ImmutableMap.Builder<Identifier, EntityAttributeData> entityAttributeData = ImmutableMap.builder();
			data.entityAttributeData.forEach(entityAttributeData::put);
			this.entityAttributeData = entityAttributeData.build();
			
			ImmutableMap.Builder<Identifier, EntityTypeData> entityTypeData = ImmutableMap.builder();
			data.entityTypeData.forEach(entityTypeData::put);
			this.entityTypeData = entityTypeData.build();
			
			this.apply();
		}, executor);
	}
	
	@Override
	public Identifier getFabricId() {
		return ID;
	}
}
