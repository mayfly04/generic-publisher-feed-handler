package com.di.kdbpublisher;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.Map;

@Data
@AllArgsConstructor
public class KdbConnection {
    private String host;
    private int port;
    private String user;
    private String password;
    private boolean ssl;

    public void insert(String table, Map<String, Object> row) {
        // Stub: Replace with actual kdb+ IPC call
        System.out.printf("Inserting into %s: %s%n", table, row);
    }
}