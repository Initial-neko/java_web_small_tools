package com.toolbox.tools.entity;

import org.junit.jupiter.api.Test;

import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityTypeMapperTest {

    private final EntityTypeMapper mapper = new EntityTypeMapper();

    @Test
    void shouldMapJdbcNumericPrecisionSafely() {
        assertEquals("Integer", mapper.fromJdbc(Types.NUMERIC, 9, 0, "NUMBER").toJavaType());
        assertEquals("Long", mapper.fromJdbc(Types.NUMERIC, 10, 0, "NUMBER").toJavaType());
        assertEquals("BigInteger", mapper.fromJdbc(Types.NUMERIC, 30, 0, "NUMBER").toJavaType());
        assertEquals("BigDecimal", mapper.fromJdbc(Types.NUMERIC, 18, 2, "NUMBER").toJavaType());
        assertEquals("BigDecimal", mapper.fromJdbc(Types.NUMERIC, 0, 0, "NUMBER").toJavaType());
    }

    @Test
    void shouldMapBinaryAndTimeTypes() {
        assertEquals("byte[]", mapper.fromJdbc(Types.BLOB, 0, 0, "BLOB").toJavaType());
        assertEquals("LocalTime", mapper.fromJdbc(Types.TIME, 0, 0, "TIME").toJavaType());
        assertEquals("LocalDateTime", mapper.fromJdbc(Types.TIMESTAMP, 0, 0, "TIMESTAMP").toJavaType());
    }
}
