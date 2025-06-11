package com.di.fixadapter;

import lombok.Data;
import java.util.List;

@Data
public class MessageMapping {
    private String msgType;
    private String table;
    private List<ColumnMapping> columns;
}