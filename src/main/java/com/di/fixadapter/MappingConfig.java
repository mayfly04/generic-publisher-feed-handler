package com.di.fixadapter;

import lombok.Data;
import java.util.List;

@Data
public class MappingConfig {
    private List<MessageMapping> messageMappings;
}