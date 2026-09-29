package com.toolbox.tools.entity;

public class TypeSchema {

    private final ValueKind kind;
    private final EntitySchema objectSchema;
    private final TypeSchema itemType;

    private TypeSchema(ValueKind kind, EntitySchema objectSchema, TypeSchema itemType) {
        this.kind = kind;
        this.objectSchema = objectSchema;
        this.itemType = itemType;
    }

    public static TypeSchema scalar(ValueKind kind) {
        return new TypeSchema(kind, null, null);
    }

    public static TypeSchema object(EntitySchema schema) {
        return new TypeSchema(ValueKind.OBJECT, schema, null);
    }

    public static TypeSchema list(TypeSchema itemType) {
        return new TypeSchema(ValueKind.LIST, null,
                itemType == null ? scalar(ValueKind.UNKNOWN) : itemType);
    }

    public ValueKind getKind() {
        return kind;
    }

    public EntitySchema getObjectSchema() {
        return objectSchema;
    }

    public TypeSchema getItemType() {
        return itemType;
    }

    public String toJavaType() {
        switch (kind) {
            case STRING: return "String";
            case INTEGER: return "Integer";
            case LONG: return "Long";
            case BIG_INTEGER: return "BigInteger";
            case BIG_DECIMAL: return "BigDecimal";
            case BOOLEAN: return "Boolean";
            case LOCAL_DATE: return "LocalDate";
            case LOCAL_TIME: return "LocalTime";
            case LOCAL_DATE_TIME: return "LocalDateTime";
            case BYTE_ARRAY: return "byte[]";
            case OBJECT: return objectSchema == null ? "Object" : objectSchema.getClassName();
            case LIST: return "List<" + itemType.toJavaType() + ">";
            default: return "Object";
        }
    }
}
