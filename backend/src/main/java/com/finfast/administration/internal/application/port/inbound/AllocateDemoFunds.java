package com.finfast.administration.internal.application.port.inbound;

import com.finfast.administration.internal.application.dto.AllocationCommand;
import com.finfast.administration.internal.application.dto.AllocationResult;

/** Administration-local allocation entry contract; not a cross-module capability.
 * Invoke the configured transactional proxy. New Pending results await the outermost commit.
 */
public interface AllocateDemoFunds {
    AllocationResult allocate(AllocationCommand command);
}
