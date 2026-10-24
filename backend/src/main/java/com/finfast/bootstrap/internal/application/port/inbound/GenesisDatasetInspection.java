package com.finfast.bootstrap.internal.application.port.inbound;

/**
 * Inbound port to inspect whether the GENESIS dataset has already been initialized.
 */
public interface GenesisDatasetInspection {
    boolean isGenesisInitialized();
}

