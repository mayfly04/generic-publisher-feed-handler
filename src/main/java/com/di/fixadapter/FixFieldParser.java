package com.di.fixadapter;

import quickfix.FieldMap;

public interface FixFieldParser {
    Object parse(FieldMap message, int tag, String rawValue);
}