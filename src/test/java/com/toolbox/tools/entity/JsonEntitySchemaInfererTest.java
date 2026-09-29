package com.toolbox.tools.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JsonEntitySchemaInfererTest {

    private final JsonEntitySchemaInferer inferer = new JsonEntitySchemaInferer();

    @Test
    void shouldInferScalarTypes() {
        EntitySchema schema = inferer.infer(
                "{\"id\":1,\"large\":2147483648,\"amount\":12.34,\"active\":true,\"name\":\"A\",\"empty\":null}",
                "User", "demo");

        assertEquals("Integer", field(schema, "id").getJavaType());
        assertEquals("Long", field(schema, "large").getJavaType());
        assertEquals("BigDecimal", field(schema, "amount").getJavaType());
        assertEquals("Boolean", field(schema, "active").getJavaType());
        assertEquals("String", field(schema, "name").getJavaType());
        assertEquals("Object", field(schema, "empty").getJavaType());
    }

    @Test
    void shouldInferNestedObjectAndMergeObjectArraySamples() {
        EntitySchema schema = inferer.infer(
                "{\"address\":{\"city\":\"Tokyo\"},\"orders\":[{\"id\":1},{\"id\":2,\"note\":\"x\"}]}",
                "User", "demo");

        FieldSchema address = field(schema, "address");
        assertEquals(ValueKind.OBJECT, address.getType().getKind());
        assertEquals("Address", address.getJavaType());
        assertEquals("String", field(address.getType().getObjectSchema(), "city").getJavaType());

        FieldSchema orders = field(schema, "orders");
        assertEquals(ValueKind.LIST, orders.getType().getKind());
        assertEquals("List<OrdersItem>", orders.getJavaType());
        EntitySchema item = orders.getType().getItemType().getObjectSchema();
        assertNotNull(item);
        assertEquals("Integer", field(item, "id").getJavaType());
        assertEquals("String", field(item, "note").getJavaType());
    }

    @Test
    void shouldHandleNamesAndCollisionsDeterministically() {
        EntitySchema schema = inferer.infer(
                "{\"user-id\":1,\"user_id\":2,\"class\":\"x\",\"123name\":\"y\"}",
                "123-user", "demo");

        assertEquals("Entity123User", schema.getClassName());
        assertEquals("userId", field(schema, "user-id").getFieldName());
        assertEquals("userId2", field(schema, "user_id").getFieldName());
        assertEquals("classValue", field(schema, "class").getFieldName());
        assertEquals("field123name", field(schema, "123name").getFieldName());
    }

    @Test
    void shouldFallbackMixedArrayToObject() {
        EntitySchema schema = inferer.infer(
                "{\"values\":[1,\"x\",true]}",
                "Sample", "demo");

        assertEquals("List<Object>", field(schema, "values").getJavaType());
    }

    private FieldSchema field(EntitySchema schema, String sourceName) {
        for (FieldSchema field : schema.getFields()) {
            if (sourceName.equals(field.getSourceName())) {
                return field;
            }
        }
        throw new AssertionError("field not found: " + sourceName);
    }
}
