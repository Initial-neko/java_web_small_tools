package com.toolbox.tools.entity;

public class FieldSchema {

    private final String sourceName;
    private final String fieldName;
    private final TypeSchema type;
    private final String sourceType;
    private final String comment;

    public FieldSchema(String sourceName, String fieldName, TypeSchema type) {
        this(sourceName, fieldName, type, null, null);
    }

    public FieldSchema(String sourceName, String fieldName, TypeSchema type,
                       String sourceType, String comment) {
        this.sourceName = sourceName;
        this.fieldName = fieldName;
        this.type = type;
        this.sourceType = sourceType;
        this.comment = comment;
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

    public String getSourceType() {
        return sourceType;
    }

    public String getComment() {
        return comment;
    }
}
