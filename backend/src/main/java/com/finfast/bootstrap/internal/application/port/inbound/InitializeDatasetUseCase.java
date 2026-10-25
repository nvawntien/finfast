package com.finfast.bootstrap.internal.application.port.inbound;

/** Explicit protected operator setup only; never invoked by application startup or ordinary HTTP. */
public interface InitializeDatasetUseCase {
    void initializeDataset();
}

