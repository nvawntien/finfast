package com.finfast.bootstrap.internal;

import com.finfast.accounts.api.TreasuryProvisioning;
import com.finfast.common.money.VndAmount;
import com.finfast.ledger.api.GenesisPosting;
import com.finfast.ledger.api.PostingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnBean({TreasuryProvisioning.class, GenesisPosting.class})
public class TreasuryBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TreasuryBootstrap.class);

    private final TreasuryProvisioning treasuryProvisioning;
    private final GenesisPosting genesisPosting;
    private final long initialSupply;

    public TreasuryBootstrap(
            TreasuryProvisioning treasuryProvisioning,
            GenesisPosting genesisPosting,
            @Value("${finfast.treasury.initial-supply:10000000000}") long initialSupply) {
        this.treasuryProvisioning = treasuryProvisioning;
        this.genesisPosting = genesisPosting;
        this.initialSupply = initialSupply;
    }

    @Override
    public void run(String... args) {
        log.info("Bắt đầu tiến trình khởi tạo Treasury (Bootstrap)...");
        try {
            UUID treasuryId = treasuryProvisioning.openTreasury();
            VndAmount supplyAmount = new VndAmount(initialSupply);

            PostingResult.Pending result = genesisPosting.initialize(treasuryId, supplyAmount);
            log.info("Khởi tạo quỹ Treasury thành công: txId={}, reference={}, số dư: {} VND",
                    result.transactionId(), result.reference(), result.amount().value());
        } catch (IllegalStateException e) {
            log.info("Treasury đã được khởi tạo trước đó. Bỏ qua cấp vốn. Chi tiết: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Lỗi hệ thống khi khởi tạo Treasury: ", e);
        }
    }
}
