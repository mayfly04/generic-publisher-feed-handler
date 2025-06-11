package com.di.connection;

import kx.insights.streaming.BulkLoader;
import kx.insights.streaming.StreamingClient;
import kx.insights.streaming.StreamingClientFactory;
import kx.insights.streaming.rt.RtClient;
import kx.insights.streaming.rt.RtClientException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Arrays;

@Slf4j
public class KdbConnectionRT implements Connection {
    private RtClient rtClient;
    private BulkLoader bulkLoader;

    private static final String TABLE_NAME = "t_kgsd_fx_fwd_realtime_digitec";
    private static final String[] COLUMNS = {
            "time", "rcvTime", "reqID", "sym", "symbolSfx", "noMDEntries", "side",
            "price", "size", "entryDate", "quoteCondition", "settlDate", "forwardPoints",
            "pip", "tenorValue", "spotVDate", "origin"
    };

    @Override
    public void openConnection() throws IOException {
        try {
            // Uses KXI_CONFIG_URL from environment automatically
            StreamingClientFactory factory = new StreamingClientFactory();
            StreamingClient streamingClient = factory.getStreamingClient();
            rtClient = (RtClient) streamingClient;
            rtClient.start();
            log.info("KDB RT client initialized: {}", rtClient);
            bulkLoader = new BulkLoader(TABLE_NAME, rtClient, COLUMNS);
        } catch (Exception e) {
            throw new IOException("Failed to open KDB RT connection: " + e.getMessage(), e);
        }
    }

    @Override
    public void closeConnection() throws IOException {
        if (rtClient != null) {
            try {
                rtClient.stop();
            } catch (RtClientException e) {
                throw new IOException("Failed to stop KDB RT client: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void insertBatch(Object[][] data) throws IOException {
        if (bulkLoader == null) {
            throw new IOException("KDB RT connection is not initialized");
        }

        if (data == null) {
            throw new IOException("Data batch is null.");
        }

        if (data.length == 0) {
            log.warn("insertBatch called with empty data array. Skipping insert.");
            return;
        }

        for (int i = 0; i < data.length; i++) {
            Object[] row = data[i];

            if (row == null) {
                throw new IOException("Row " + i + " is null in data batch.");
            }

            if (row.length != COLUMNS.length) {
                throw new IOException(String.format(
                        "Row %d has incorrect column count. Expected %d, got %d. Row: %s",
                        i, COLUMNS.length, row.length, Arrays.toString(row)
                ));
            }

            for (int j = 0; j < row.length; j++) {
                if (row[j] == null) {
                    log.warn("Row {} column {} ({}) is null", i, j, COLUMNS[j]);
                }
            }
        }

        try {
            bulkLoader.writeTable(data);
            log.debug("Inserted {} rows into KDB RT", data.length);
        } catch (Exception e) {
            throw new IOException("Failed to insert data into KDB RT: " + e.getMessage(), e);
        }
    }
}