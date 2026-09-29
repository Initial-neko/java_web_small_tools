package com.toolbox.tools.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EntitySchema {

    private final String packageName;
    private final String className;
    private final List<FieldSchema> fields;

    public EntitySchema(String packageName, String className, List<FieldSchema> fields) {
        this.packageName = packageName == null ? "" : packageName;
        this.className = className;
        this.fields = Collections.unmodifiableList(new ArrayList<FieldSchema>(fields));
    }

    public String getPackageName() {
        return packageName;
    }

    public String getClassName() {
        return className;
    }

    public List<FieldSchema> getFields() {
        return fields;
    }
}
