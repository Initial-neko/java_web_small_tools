package com.toolbox.tools.metric;

public class DimensionCandidate {

    private String name;
    private String expression;

    public DimensionCandidate() {
    }

    public DimensionCandidate(String name, String expression) {
        this.name = name;
        this.expression = expression;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }
}
