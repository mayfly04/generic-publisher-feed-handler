package com.di.fixadapter.parsers;

import com.di.fixadapter.FixFieldParser;
import quickfix.FieldMap;

public class RemoveSlashParser implements FixFieldParser {
    @Override
    public Object parse(FieldMap message, int tag, String rawValue) {
        return rawValue.replace("/", "");
    }
}