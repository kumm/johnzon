/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.johnzon.mapper;

import org.apache.johnzon.mapper.access.AccessMode;
import org.apache.johnzon.mapper.converter.EnumConverter;
import org.apache.johnzon.mapper.internal.AdapterKey;
import org.apache.johnzon.mapper.internal.ConverterAdapter;

import javax.json.JsonObject;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * Contains internal configuration for all the mapper stuff.
 * It needs to be immutable and 100% runtime oriented.
 */
class MapperConfig implements Cloneable {

    private static final ObjectConverter NO_CONVERTER = new ObjectConverter() {
        @Override
        public void writeJson(Object instance, MappingGenerator jsonbGenerator) {
            // just a dummy
        }

        @Override
        public Object fromJson(JsonObject jsonObject, Type targetType, MappingParser parser) {
            return null;
        }
    };

    private final int version;
    private final boolean close;
    private final boolean skipNull;
    private final boolean skipEmptyArray;
    private final boolean treatByteArrayAsBase64;
    private final boolean treatByteArrayAsBase64URL;
    private final boolean readAttributeBeforeWrite;
    private final AccessMode accessMode;
    private final Charset encoding;
    private final ConcurrentMap<AdapterKey, Adapter<?, ?>> adapters;
    private final Map<Class<?>, ObjectConverter<?>> objectConverters;
    private final Comparator<String> attributeOrder;

    private final Map<Class<?>, ObjectConverter<?>> objectConverterCache;

    //disable checkstyle for 10+ parameters
    //CHECKSTYLE:OFF
    public MapperConfig(final ConcurrentMap<AdapterKey, Adapter<?, ?>> adapters,
                        final Map<Class<?>, ObjectConverter<?>> objectConverters,
                        final int version, final boolean close,
                        final boolean skipNull, final boolean skipEmptyArray,
                        final boolean treatByteArrayAsBase64, final boolean treatByteArrayAsBase64URL,
                        final boolean readAttributeBeforeWrite,
                        final AccessMode accessMode, final Charset encoding,
                        final Comparator<String> attributeOrder) {
    //CHECKSTYLE:ON
        this.objectConverters = objectConverters;
        this.version = version;
        this.close = close;
        this.skipNull = skipNull;
        this.skipEmptyArray = skipEmptyArray;
        this.treatByteArrayAsBase64 = treatByteArrayAsBase64;
        this.treatByteArrayAsBase64URL = treatByteArrayAsBase64URL;
        this.readAttributeBeforeWrite = readAttributeBeforeWrite;
        this.accessMode = accessMode;
        this.encoding = encoding;
        this.adapters = adapters;
        this.attributeOrder = attributeOrder;

        this.objectConverterCache = new HashMap<Class<?>, ObjectConverter<?>>(objectConverters.size());
    }

    public Adapter findAdapter(final Type aClass) {
        final Adapter<?, ?> converter = adapters.get(new AdapterKey(aClass, String.class));
        if (converter != null) {
            return converter;
        }
        if (Class.class.isInstance(aClass)) {
            final Class<?> clazz = Class.class.cast(aClass);
            if (clazz.isEnum()) {
                final Adapter<?, ?> enumConverter = new ConverterAdapter(new EnumConverter(clazz));
                adapters.putIfAbsent(new AdapterKey(String.class, aClass), enumConverter);
                return enumConverter;
            }
        }
        return null;
    }

    /**
     * Search for an {@link ObjectConverter} for the given class.
     *
     * If no {@link ObjectConverter} was found for the specific class,
     * the whole type hierarchy will be scanned for a matching {@link ObjectConverter}.
     *
     * In case the given class implements more than on interfaces and for at least two
     * we have configured an {@link ObjectConverter} the {@link ObjectConverter} for the
     * first interface we get will be taken.
     *
     * @param clazz the {@link Class}
     *
     * @return the found {@link ObjectConverter} or {@code null} if no {@link ObjectConverter} has been found
     *
     * @throws IllegalArgumentException if {@code clazz} is {@code null}
     */
    public ObjectConverter findObjectConverter(Class clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("clazz must not be null");
        }

        // first lets look in our cache
        ObjectConverter<?> converter = objectConverterCache.get(clazz);
        if (converter != null && converter != NO_CONVERTER) {
            return converter;
        }

        // if we have found a dummy, we return null
        if (converter == NO_CONVERTER) {
            return null;
        }

        // we get called the first time for this class
        // lets search...

        Map<Class<?>, ObjectConverter<?>> matchingConverters = new HashMap<Class<?>, ObjectConverter<?>>();

        for (Map.Entry<Class<?>, ObjectConverter<?>> entry : objectConverters.entrySet()) {

            if (clazz == entry.getKey()) {
                converter = entry.getValue();
                break;
            }

            if (entry.getKey().isAssignableFrom(clazz)) {
                matchingConverters.put(entry.getKey(), entry.getValue());
            }
        }

        if (converter != null) {
            objectConverterCache.put(clazz, converter);
            return converter;
        }

        if (matchingConverters.isEmpty()) {
            objectConverterCache.put(clazz, NO_CONVERTER);
            return null;
        }

        // search the most significant
        Class toProcess = clazz;
        while (toProcess != Object.class && converter == null) {

            converter = matchingConverters.get(toProcess);
            if (converter != null) {
                break;
            }

            for (Class interfaceToSearch : toProcess.getInterfaces()) {

                converter = matchingConverters.get(interfaceToSearch);
                if (converter != null) {
                    break;
                }
            }

            toProcess = toProcess.getSuperclass();
        }

        if (converter == null) {
            objectConverterCache.put(clazz, NO_CONVERTER);
        } else {
            objectConverterCache.put(clazz, converter);
        }

        return converter;
    }

    public int getVersion() {
        return version;
    }

    public boolean isClose() {
        return close;
    }

    public boolean isSkipNull() {
        return skipNull;
    }

    public boolean isSkipEmptyArray() {
        return skipEmptyArray;
    }

    public boolean isTreatByteArrayAsBase64() {
        return treatByteArrayAsBase64;
    }

    public boolean isTreatByteArrayAsBase64URL() {
        return treatByteArrayAsBase64URL;
    }

    public boolean isReadAttributeBeforeWrite() {
        return readAttributeBeforeWrite;
    }

    public AccessMode getAccessMode() {
        return accessMode;
    }

    public Charset getEncoding() {
        return encoding;
    }

    public ConcurrentMap<AdapterKey, Adapter<?, ?>> getAdapters() {
        return adapters;
    }

    public Map<Class<?>, ObjectConverter<?>> getObjectConverters() {
        return objectConverters;
    }

    public Comparator<String> getAttributeOrder() {
        return attributeOrder;
    }
}