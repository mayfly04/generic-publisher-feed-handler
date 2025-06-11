package com.di.kdbpublisher;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class KdbPublisher {
    private static final Logger logger = LoggerFactory.getLogger(KdbPublisher.class);

    private final KdbConnection connection;
    private final int batchSize;
    private final boolean async;

    public void publishRow(String table, Map<String, Object> row) {
        logger.info("Publishing row to {}: {}", table, row);
        connection.insert(table, row);
    }

    public void publishBatch(String table, List<Map<String, Object>> rows) {
        logger.info("Publishing batch of {} rows to {}", rows.size(), table);
        for (Map<String, Object> row : rows) {
            connection.insert(table, row);
        }
    }
}