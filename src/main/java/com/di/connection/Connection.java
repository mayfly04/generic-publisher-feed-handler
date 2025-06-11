package com.di.connection;

import java.io.IOException;

public interface Connection {
    void openConnection() throws IOException;

    void closeConnection() throws IOException;

    void insertBatch(Object[][] insertedRows) throws IOException;

}