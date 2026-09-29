package com.toolbox.tools.entity;

public class FieldSchema {

    private final String sourceName;
    private final String fieldName;
    private final TypeSchema type;

    public FieldSchema(String sourceName, String fieldName, TypeSchema type) {
        this.sourceName = sourceName;
        this.fieldName = fieldName;
        this.type = type;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public TypeSchema getType() {
        return type;
    }

    public String getJavaType() {
        return type.toJavaType();
    }
}
