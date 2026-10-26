package com.finfast.accounts.internal.application.usecase;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.accounts.api.TreasuryProvisioning;
import com.finfast.accounts.internal.application.port.outbound.AccountRepositoryPort;
import com.finfast.accounts.internal.domain.entity.Account;
import com.finfast.accounts.internal.domain.exception.AccountErrorCode;
import com.finfast.accounts.internal.domain.service.AccountNumberGenerator;
import com.finfast.common.error.ApplicationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * Service implementing Treasury Account provisioning for bootstrap/setup.
 * Joins the existing outer setup transaction and creates a single zero-balance Treasury.
 */
@Service
public class TreasuryProvisioningService implements TreasuryProvisioning {

    private final AccountRepositoryPort repository;
    private final AccountNumberGenerator numberGenerator;

    @Autowired
    public TreasuryProvisioningService(AccountRepositoryPort repository) {
        this(repository, AccountNumberGenerator.defaultGenerator());
    }

    public TreasuryProvisioningService(AccountRepositoryPort repository, AccountNumberGenerator numberGenerator) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.numberGenerator = Objects.requireNonNull(numberGenerator, "numberGenerator");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public UUID openTreasury() {
        if (repository.existsTreasury()) {
            throw new ApplicationException(AccountErrorCode.TREASURY_ALREADY_EXISTS);
        }

        UUID treasuryId = UUID.randomUUID();
        AccountNumber treasuryNumber = numberGenerator.treasuryNumber();
        Account treasury = Account.openTreasury(treasuryId, treasuryNumber);

        repository.insert(treasury);
        return treasuryId;
    }
}
