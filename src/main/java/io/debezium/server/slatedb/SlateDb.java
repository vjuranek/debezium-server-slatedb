/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.server.slatedb;

/**
 *
 * @author vjuranek
 */
public interface SlateDb {
    void init(String path);

    void insert(String key, String value);

    void close();
}