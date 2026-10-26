package com.finfast.identity.internal.application.port.inbound;

import com.finfast.identity.internal.application.dto.SessionPrincipal;

/** Internal credential-verification entry; only verified identity may become a session principal. */
public interface UserAuthentication {
    SessionPrincipal authenticate(String email, String password);
}
