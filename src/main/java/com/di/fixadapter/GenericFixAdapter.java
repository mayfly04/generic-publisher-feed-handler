package com.di.fixadapter;

import quickfix.*;
import quickfix.field.MsgType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.stream.Collectors;
import java.io.*;

public class GenericFixAdapter extends MessageCracker implements Application {
    private static final Logger logger = LoggerFactory.getLogger(GenericFixAdapter.class);

    private final Map<String, MessageMapping> mappings;
    private final com.di.kdbpublisher.KdbPublisher publisher;

    public GenericFixAdapter(String mappingConfig, com.di.kdbpublisher.KdbPublisher publisher) throws Exception {
        this.mappings = loadMappings(mappingConfig);
        this.publisher = publisher;
    }

    private Map<String, MessageMapping> loadMappings(String mappingConfig) throws IOException {
        var mapper = new ObjectMapper(new YAMLFactory());
        var config = mapper.readValue(new File(mappingConfig), MappingConfig.class);
        return config.getMessageMappings().stream().collect(Collectors.toMap(m -> m.getMsgType(), m -> m));      
    }

    @Override
    public void fromApp(Message message, SessionID sessionId) {
        try {
            String msgType = message.getHeader().getString(MsgType.FIELD);
            MessageMapping mapping = mappings.get(msgType);
            if (mapping != null) {
                var row = new LinkedHashMap<String, Object>();
                for (ColumnMapping col : mapping.getColumns()) {
                    String val = message.getString(col.getTag());
                    Object parsed;
                    if (col.getParserClass() != null && !col.getParserClass().isEmpty()) {
                        FixFieldParser parser = (FixFieldParser) Class.forName(col.getParserClass()).getDeclaredConstructor().newInstance();
                        parsed = parser.parse(message, col.getTag(), val);
                    } else {
                        parsed = castValue(val, col.getType());
                    }
                    row.put(col.getName(), parsed);
                }
                publisher.publishRow(mapping.getTable(), row);
            }
        } catch (Exception e) {
            logger.error("Error processing FIX message", e);
        }
    }

    private Object castValue(String val, String type) {
        return switch (type) {
            case "int" -> Integer.parseInt(val);
            case "long" -> Long.parseLong(val);
            case "double" -> Double.parseDouble(val);
            default -> val;
        };
    }

    
    @Override public void onCreate(SessionID sessionId) {}
    @Override public void onLogon(SessionID sessionId) {}
    @Override public void onLogout(SessionID sessionId) {}
    @Override public void toAdmin(Message message, SessionID sessionId) {}
    @Override public void fromAdmin(Message message, SessionID sessionId) {}
    @Override public void toApp(Message message, SessionID sessionId) {}
}