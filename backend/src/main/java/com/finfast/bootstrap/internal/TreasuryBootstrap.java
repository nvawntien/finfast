package com.finfast.bootstrap.internal;

import com.finfast.common.money.VndAmount;
import com.finfast.ledger.api.GenesisPosting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class TreasuryBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TreasuryBootstrap.class);
    private final GenesisPosting genesisPosting;

    public TreasuryBootstrap(GenesisPosting genesisPosting) {
        this.genesisPosting = genesisPosting;
    }

    @Override
    public void run(String... args) {
        log.info("Bắt đầu tiến trình khởi tạo Treasury (Bootstrap)...");
        try {
            long initialSupply = 10000000000L; 
            UUID treasuryId = UUID.randomUUID(); 

            VndAmount supplyAmount = new VndAmount(initialSupply);
            genesisPosting.initialize(treasuryId, supplyAmount);
            
            log.info("Khởi tạo quỹ Treasury thành công với số dư: {} VND", initialSupply);
        } catch (IllegalStateException e) {
            log.info("Treasury đã được khởi tạo trước đó. Bỏ qua cấp vốn. Chi tiết: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Lỗi hệ thống khi khởi tạo Treasury: ", e);
        }
    }
}