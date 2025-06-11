package com.di.helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class PropertiesHelper {
    protected static final Logger LOG = LoggerFactory.getLogger(PropertiesHelper.class);
    public static Properties loadProperties(String path) {
        Properties properties = new Properties();
        try (InputStream input = new FileInputStream(path)) {
            properties.load(input);
        } catch (IOException ex) {
            LOG.error("Failed to load properties file: " + ex.getMessage());
        }
        return properties;
    }
}
