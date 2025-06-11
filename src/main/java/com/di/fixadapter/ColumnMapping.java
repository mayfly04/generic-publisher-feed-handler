package com.di.fixadapter;

import lombok.Data;

@Data
public class ColumnMapping {
    private String name;
    private int tag;
    private String type;
    private String parserClass; // Optional
}