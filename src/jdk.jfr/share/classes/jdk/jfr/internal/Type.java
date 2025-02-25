/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.jfr.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jdk.internal.module.Checks;
import jdk.jfr.AnnotationElement;
import jdk.jfr.Event;
import jdk.jfr.SettingControl;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.internal.util.Utils;

/**
 * Internal data structure that describes a type,
 *
 * Used to create event types, value descriptor and annotations.
 *
 */
public class Type implements Comparable<Type> {
    public static final String SUPER_TYPE_ANNOTATION = java.lang.annotation.Annotation.class.getName();
    public static final String SUPER_TYPE_SETTING = SettingControl.class.getName();
    public static final String SUPER_TYPE_EVENT = Event.class.getName();
    public static final String EVENT_NAME_PREFIX = "jdk.";
    public static final String TYPES_PREFIX = "jdk.types.";
    public static final String SETTINGS_PREFIX = "jdk.settings.";


    // To bootstrap the type system, the supported Java types
    // are available here as statics. When metadata.xml is parsed
    // fields are added to THREAD and STACK_TRACE.
    private static final Map<Type, Class<?>> knownTypes = new LinkedHashMap<>();
    static final Type BOOLEAN = createKnownType(boolean.class);
    static final Type CHAR = createKnownType(char.class);
    static final Type FLOAT = createKnownType(float.class);
    static final Type DOUBLE = createKnownType(double.class);
    static final Type BYTE = createKnownType(byte.class);
    static final Type SHORT = createKnownType(short.class);
    static final Type INT = createKnownType(int.class);
    static final Type LONG = createKnownType(long.class);
    static final Type CLASS = createKnownType(Class.class);
    static final Type STRING = createKnownType(String.class);
    static final Type THREAD = createKnownType(Thread.class);
    public static final Type STACK_TRACE = createKnownType(TYPES_PREFIX + "StackTrace", null);

    private static Type createKnownType(Class<?> clazz) {
        return createKnownType(clazz.getName(), clazz);
    }

    private static Type createKnownType(String name, Class<?> clazz) {
        long id = JVM.getTypeId(name);
        Type t =  new Type(name, null, id, null);
        knownTypes.put(t, clazz);
        return t;
    }

    private final AnnotationConstruct annos = new AnnotationConstruct();
    private final String name;
    private final String superType;
    private List<ValueDescriptor> fields = new ArrayList<>();
    private Boolean simpleType; // calculated lazy
    private boolean remove = true;
    private long id;
    private boolean visible = true;
    private boolean internal;

    /**
     * Creates a type
     *
     * @param javaTypeName i.e "java.lang.String"
     * @param superType i.e "java.lang.Annotation"
     * @param typeId the class id that represents the class in the JVM
     *
     */
    public Type(String javaTypeName, String superType, long typeId) {
        this(javaTypeName, superType, typeId, null);
        if (!Checks.isClassName(javaTypeName)) {
            // Should not be able to come here with an invalid type name
            throw new InternalError(javaTypeName + " is not a valid Java type");
        }
    }

    Type(String javaTypeName, String superType, long typeId, Boolean simpleType) {
        Objects.requireNonNull(javaTypeName);
        this.superType = superType;
        this.name = javaTypeName;
        this.id = typeId;
        this.simpleType = simpleType;
    }

    static boolean isDefinedByJVM(long id) {
        return id < JVM.RESERVED_CLASS_ID_LIMIT;
    }

    public static long getTypeId(Class<?> clazz) {
        Type type = Type.getKnownType(clazz);
        return type == null ? JVM.getTypeId(clazz) : type.getId();
    }

    static Collection<Type> getKnownTypes() {
        return knownTypes.keySet();
    }

    public static boolean isValidJavaFieldType(String name) {
        for (Map.Entry<Type, Class<?>> entry : knownTypes.entrySet()) {
            Class<?> clazz = entry.getValue();
            if (clazz != null && name.equals(clazz.getName())) {
                return true;
            }
        }
        return false;
    }

    public static Type getKnownType(String typeName) {
        for (Type type : knownTypes.keySet()) {
            if (type.getName().equals(typeName)) {
                return type;
            }
        }
        return null;
    }

    static boolean isKnownType(Class<?> type) {
        if (type.isPrimitive()) {
            return true;
        }
        if (type.equals(Class.class) || type.equals(Thread.class) || type.equals(String.class)) {
            return true;
        }
        return false;
    }

    public static Type getKnownType(Class<?> clazz) {
        for (Map.Entry<Type, Class<?>> entry : knownTypes.entrySet()) {
            if (clazz != null && clazz.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    public String getName() {
        return name;
    }

    public String getLogName() {
       return getName() + "(" + getId() + ")";
    }

    public ValueDescriptor getField(String name) {
        int dotIndex = name.indexOf(".");
        if (dotIndex > 0) {
            String pre = name.substring(0, dotIndex);
            String post = name.substring(dotIndex + 1);
            ValueDescriptor subField = getField(pre);
            if (subField != null) {
                Type type = PrivateAccess.getInstance().getType(subField);
                return type.getField(post);
            }
            return null;
        }
        return Utils.findField(getFields(), name);
    }

    public List<ValueDescriptor> getFields() {
        if (fields instanceof ArrayList<?> list) {
            list.trimToSize();
            fields = Collections.unmodifiableList(fields);
        }
        return fields;
    }

    public boolean isSimpleType() {
        if (simpleType == null) {
            simpleType = calculateSimpleType();
        }
        return simpleType.booleanValue();
    }

    private boolean calculateSimpleType() {
        if (fields.size() != 1) {
            return false;
        }
        // annotation, settings and event can never be simple types
        return superType == null;
    }

    public boolean isDefinedByJVM() {
        return id < JVM.RESERVED_CLASS_ID_LIMIT;
    }

    public void setFields(List<ValueDescriptor> fields) {
        this.fields = List.copyOf(fields);
    }

    public void add(ValueDescriptor valueDescriptor) {
        Objects.requireNonNull(valueDescriptor);
        fields.add(valueDescriptor);
    }

    public int indexOf(String name) {
        for (int i = 0; i < fields.size(); i++) {
            if (name.equals(fields.get(i).getName())) {
                return i;
            }
        }
        return -1;
    }

    void trimFields() {
        getFields();
    }

    void setAnnotations(List<AnnotationElement> annotations) {
        annos.setAnnotationElements(annotations);
    }

    public String getSuperType() {
        return superType;
    }

    public long getId() {
        return id;
    }

    public String getLabel() {
        return annos.getLabel();
    }

    public List<AnnotationElement> getAnnotationElements() {
        return annos.getUnmodifiableAnnotationElements();
    }

    public <T> T getAnnotationValue(Class<? extends java.lang.annotation.Annotation> clazz, T defaultValue) {
       return annos.getAnnotationValue(clazz, defaultValue);
    }

    public <T> T getAnnotation(Class<? extends java.lang.annotation.Annotation> clazz) {
        return annos.getAnnotation(clazz);
    }

    public String getDescription() {
        return annos.getDescription();
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof Type that) {
            return that.id == this.id;
        }
        return false;
    }

    @Override
    public int compareTo(Type that) {
        return Long.compare(this.id, that.id);
    }

    void log(String action, LogTag logTag, LogLevel level) {
        if (Logger.shouldLog(logTag, level) && !isSimpleType()) {
            Logger.log(logTag, LogLevel.TRACE, action + " " + typeText() + " " + getLogName() + " {");
            for (ValueDescriptor v : getFields()) {
                String array = v.isArray() ? "[]" : "";
                Logger.log(logTag, LogLevel.TRACE, "  " + v.getTypeName() + array + " " + v.getName() + ";");
            }
            Logger.log(logTag, LogLevel.TRACE, "}");
        } else {
            if (Logger.shouldLog(logTag, LogLevel.INFO) && !isSimpleType()) {
                Logger.log(logTag, LogLevel.INFO, action + " " + typeText() + " " + getLogName());
            }
        }
    }

    private String typeText() {
        if (this instanceof PlatformEventType) {
            return "event type";
        }
        if (Type.SUPER_TYPE_SETTING.equals(superType)) {
            return "setting type";
        }
        if (Type.SUPER_TYPE_ANNOTATION.equals(superType)) {
            return "annotation type";
        }
        return "type";
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getLogName());
        if (!getFields().isEmpty()) {
            sb.append(" {\n");
            for (ValueDescriptor td : getFields()) {
                sb.append("  type=" + td.getTypeName() + "(" + td.getTypeId() + ") name=" + td.getName() + "\n");
            }
            sb.append("}\n");
        }
        return sb.toString();
    }

    public void setRemove(boolean remove) {
       this.remove = remove;
    }

    public boolean getRemove() {
        return remove;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setInternal(boolean internal) {
        this.internal = internal;
    }

    public boolean isInternal() {
        return internal;
    }

    public boolean hasAnnotation(Class<? extends java.lang.annotation.Annotation> clazz) {
        return annos.getAnnotationElement(clazz) != null;
    }
}
